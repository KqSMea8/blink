/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.plan.batch.sql.validation

import org.apache.flink.api.scala._
import org.apache.flink.table.api.scala._
import org.apache.flink.table.api.{TableException, ValidationException}
import org.apache.flink.table.util.TableTestBase
import org.junit.Test

class JoinValidationTest extends TableTestBase {

  private val util = batchTestUtil()

  @Test(expected = classOf[ValidationException])
  def testJoinNonExistingKey(): Unit = {
    util.addTable[(Int, Long, String)]("Table3", 'a, 'b, 'c)
    util.addTable[(Int, Long, Int, String, Long)]("Table5", 'd, 'e, 'f, 'g, 'h)

    val sqlQuery = "SELECT c, g FROM Table3, Table5 WHERE foo = e"

    util.tableEnv.sqlQuery(sqlQuery)
  }

  @Test // Will do implicit type coercion.
  def testJoinNonMatchingKeyTypes(): Unit = {
    util.addTable[(Int, Long, String)]("Table3", 'a, 'b, 'c)
    util.addTable[(Int, Long, Int, String, Long)]("Table5", 'd, 'e, 'f, 'g, 'h)

    val sqlQuery = "SELECT c, g FROM Table3, Table5 WHERE a = g"

    util.tableEnv.sqlQuery(sqlQuery)
  }

  @Test(expected = classOf[ValidationException])
  def testJoinWithAmbiguousFields(): Unit = {
    
    util.addTable[(Int, Long, String)]("Table3", 'a, 'b, 'c)
    util.addTable[(Int, Long, Int, String, Long)]("Table5", 'd, 'e, 'f, 'g, 'c)

    val sqlQuery = "SELECT c, g FROM Table3, Table5 WHERE a = d"

    util.tableEnv.sqlQuery(sqlQuery).collect()
  }
}
