---
title: "Temporal Tables"
nav-parent_id: streaming_tableapi
nav-pos: 4
---
<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

Temporal tables represent a concept of changing table that its data is deemed to be effective or valid along some time period.

The changing table can be a changing history table where an append-only table underlies. Flink can keep track of the changes and allows for accessing the content of the table at a certain point in time within a query. Currently, this type of requirement is supported by the so-called [temporal table function join](joins.html#join-with-a-temporal-table-function).

The changing table can also be a changing dimension table where an external database underlies. Flink allows for accessing the content of the table at processing time within a query. Currently, this type of requirement is supported by the so-called [temporal table join](joins.html#join-with-a-temporal-table).

The *temporal table function join* and *temporal table join* are different in sql syntax and runtime execution, but they come from the same motivation. The difference is explained in the [join section](joins.html#join-with-a-temporal-table).

* This will be replaced by the TOC
{:toc}

## Motivation

Let's assume that we have the following table `RatesHistory`.

{% highlight sql %}
SELECT * FROM RatesHistory;

rowtime currency   rate
======= ======== ======
09:00   US Dollar   102
09:00   Euro        114
09:00   Yen           1
10:45   Euro        116
11:15   Euro        119
11:49   Pounds      108
{% endhighlight %}

`RatesHistory` represents an ever-growing append-only table of currency exchange rates with respect to `Yen` (which has a rate of `1`).
For example, the exchange rate for the period from `09:00` to `10:45` of `Euro` to `Yen` was `114`. From `10:45` to `11:15` it was `116`.

Given that we would like to output all current rates at the time `10:58`, we would need the following SQL query to compute a result table:

{% highlight sql %}
SELECT *
FROM RatesHistory AS r
WHERE r.rowtime = (
  SELECT MAX(rowtime)
  FROM RatesHistory AS r2
  WHERE r2.currency = r.currency
  AND r2.rowtime <= TIME '10:58');
{% endhighlight %}

The correlated sub-query determines the maximum time for the corresponding currency that is lower than or equal to the desired time. The outer query lists the rates that have a maximum timestamp.

The following table shows the result of such a computation. In our example, the update to `Euro` at `10:45` is taken into account. However, the update to `Euro` at `11:15` and the new entry of `Pounds` are not considered in the table's version at time `10:58`.

{% highlight text %}
rowtime currency   rate
======= ======== ======
09:00   US Dollar   102
09:00   Yen           1
10:45   Euro        116
{% endhighlight %}

The concept of *temporal tables* aims to simplify such queries, speed up their execution, and reduce Flink's state usage. A *temporal table function* is a parameterized view on an append-only table that interprets the rows of the append-only table as the changelog of a table and provides the version of that table at a specific point in time. Interpreting the append-only table as a changelog requires the specification of a primary key attribute and a timestamp attribute. The primary key determines which rows are overwritten and the timestamp determines the time during which a row is valid.

In the above example `currency` would be a primary key for `RatesHistory` table and `rowtime` would be the timestamp attribute. In Flink, this is represented by a *temporal table function*.

On the other hand, we have the requirement to join a dimension table which is a external database table. This can also be addressed by a *temporal table join*.

Let's assume that we have a table `LatestRates` (e.g. a MySQL table) that is populated with the latest rate. The above `RatesHistory` is the changelog of it. Then the content of `LatestRates` table at time `10:58` will be:

{% highlight text %}
10:58> SELECT * FROM LatestRates;
currency   rate
======== ======
US Dollar   102
Yen           1
Euro        116
{% endhighlight %}

The content of `LatestRates` table at time `12:00` will be:

{% highlight text %}
12:00> SELECT * FROM LatestRates;
currency   rate
======== ======
US Dollar   102
Yen           1
Euro        119
Pounds      108
{% endhighlight %}

In Flink, *temporal tables* only support keeping the current version of the table, so that they can only be queried at processing time.


## Temporal Table Functions

In order to access the data in a temporal table, one must pass a [time attribute](time_attributes.html) that determines the version of the returned table. Flink uses the SQL syntax of [table functions](../udfs.html#table-functions) to provide a way to express it.

Once defined, a *temporal table function* takes in a single time argument `timeAttribute` and returns a set of rows. This set contains the latest versions of the rows for all existing primary keys with respect to the given time attribute.

Assuming that we define a temporal table function `Rates(timeAttribute)` based on `RatesHistory` table, we can query such a function in the following way:

{% highlight sql %}
SELECT * FROM Rates('10:15');

rowtime currency   rate
======= ======== ======
09:00   US Dollar   102
09:00   Euro        114
09:00   Yen           1

SELECT * FROM Rates('11:00');

rowtime currency   rate
======= ======== ======
09:00   US Dollar   102
10:45   Euro        116
09:00   Yen           1
{% endhighlight %}

Each query to `Rates(timeAttribute)` will return the state of the `Rates` for the given `timeAttribute`.

**Note**: Currently, Flink doesn't support directly querying the temporal table functions with a constant time attribute parameter. At the moment, temporal table functions can only be used in joins.
The example above is used to provide an intuition about what the function `Rates(timeAttribute)` returns.

See also the page about [joins for continuous queries](joins.html) for more information about how to join with a temporal table.

### Defining Temporal Table Function

The following code snippet illustrates how to create a temporal table function from an append-only table.

<div class="codetabs" markdown="1">
<div data-lang="java" markdown="1">
{% highlight java %}
import org.apache.flink.table.functions.TemporalTableFunction;
(...)

// Get the stream and table environments.
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
StreamTableEnvironment tEnv = TableEnvironment.getTableEnvironment(env);

// Provide a static data set of the rates history table.
List<Tuple2<String, Long>> ratesHistoryData = new ArrayList<>();
ratesHistoryData.add(Tuple2.of("US Dollar", 102L));
ratesHistoryData.add(Tuple2.of("Euro", 114L));
ratesHistoryData.add(Tuple2.of("Yen", 1L));
ratesHistoryData.add(Tuple2.of("Euro", 116L));
ratesHistoryData.add(Tuple2.of("Euro", 119L));

// Create and register an example table using above data set.
// In the real setup, you should replace this with your own table.
DataStream<Tuple2<String, Long>> ratesHistoryStream = env.fromCollection(ratesHistoryData);
Table ratesHistory = tEnv.fromDataStream(ratesHistoryStream, "r_currency, r_rate, r_proctime.proctime");

tEnv.registerTable("RatesHistory", ratesHistory);

// Create and register a temporal table function.
// Define "r_proctime" as the time attribute and "r_currency" as the primary key.
TemporalTableFunction rates = ratesHistory.createTemporalTableFunction("r_proctime", "r_currency"); // <==== (1)
tEnv.registerFunction("Rates", rates);                                                              // <==== (2)
{% endhighlight %}
</div>
<div data-lang="scala" markdown="1">
{% highlight scala %}
// Get the stream and table environments.
val env = StreamExecutionEnvironment.getExecutionEnvironment
val tEnv = TableEnvironment.getTableEnvironment(env)

// Provide a static data set of the rates history table.
val ratesHistoryData = new mutable.MutableList[(String, Long)]
ratesHistoryData.+=(("US Dollar", 102L))
ratesHistoryData.+=(("Euro", 114L))
ratesHistoryData.+=(("Yen", 1L))
ratesHistoryData.+=(("Euro", 116L))
ratesHistoryData.+=(("Euro", 119L))

// Create and register an example table using above data set.
// In the real setup, you should replace this with your own table.
val ratesHistory = env
  .fromCollection(ratesHistoryData)
  .toTable(tEnv, 'r_currency, 'r_rate, 'r_proctime.proctime)

tEnv.registerTable("RatesHistory", ratesHistory)

// Create and register TemporalTableFunction.
// Define "r_proctime" as the time attribute and "r_currency" as the primary key.
val rates = ratesHistory.createTemporalTableFunction('r_proctime, 'r_currency) // <==== (1)
tEnv.registerFunction("Rates", rates)                                          // <==== (2)
{% endhighlight %}
</div>
</div>

Line `(1)` creates a `rates` [temporal table function](#temporal-table-functions),
which allows us to use the function `rates` in the [Table API](../tableApi.html#joins).

Line `(2)` registers this function under the name `Rates` in our table environment,
which allows us to use the `Rates` function in [SQL](../sql.html#joins).

## Temporal Table

In order to access data in temporal table, currently one must define a `TableSource` with `LookupableTableSource`. Flink uses the SQL syntax of `FOR SYSTEM_TIME AS OF` to query temporal table, which is proposed in SQL:2011.

Assuming that we define a temporal table `LatestRates` table, we can query such a function in the following way:

{% highlight sql %}
SELECT * FROM LatestRates FOR SYSTEM TIME AS OF TIMESTAMP '10:15';

currency   rate
======== ======
US Dollar   102
Euro        114
Yen           1

SELECT * FROM LatestRates FOR SYSTEM TIME AS OF TIMESTAMP '11:00';

currency   rate
======== ======
US Dollar   102
Euro        116
Yen           1
{% endhighlight %}

**Note**: Currently, Flink doesn't support directly querying the temporal table with a constant time. At the moment, temporal table can only be used in joins. The example above is used to provide an intuition about what the temporal table `LatestRates` returns.

See also the page about [joins for continuous queries](joins.html) for more information about how to join with a temporal table.

### Defining Temporal Table


<div class="codetabs" markdown="1">
<div data-lang="java" markdown="1">
{% highlight java %}
// Get the stream and table environments.
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
StreamTableEnvironment tEnv = TableEnvironment.getTableEnvironment(env);

// Create a CsvTableSource as an example, in production it should be a JdbcTableSource or HBaseTableSource
// which provides lookup ability. The given path file contains following items:
// US Dollar, 102
// Euro, 114
// Yen, 1
CsvTableSource rates = CsvTableSource.builder()
  .path("path/to/csv")
  .field("currency", DataTypes.STRING)
  .field("rate", DataTypes.DOUBLE)
  .uniqueKeys(singleton(singleton("currency")))  // the unique key definition is required here for lookup
  .build();

// register it into environment, then we can query it in sql
tEnv.registerTableSource("rates", rates);
{% endhighlight %}
</div>
<div data-lang="scala" markdown="1">
{% highlight scala %}
// Get the stream and table environments.
val env = StreamExecutionEnvironment.getExecutionEnvironment
val tEnv = TableEnvironment.getTableEnvironment(env)

// Create a CsvTableSource as an example, in production it should be a JdbcTableSource or HBaseTableSource
// which provides lookup ability. The given path file contains following items:
// US Dollar, 102
// Euro, 114
// Yen, 1
val rates = CsvTableSource.builder()
  .path("path/to/csv")
  .field("currency", DataTypes.STRING)
  .field("rate", DataTypes.DOUBLE)
  .uniqueKeys(singleton(singleton("currency")))
  .build()

// register it into environment, then we can query it in sql
tEnv.registerTableSource("rates", rates)
{% endhighlight %}
</div>
</div>

See also the page about [how to define LookupableTableSource](../sourceSinks.html#defining-a-tablesource-with-lookupable).

{% top %}
