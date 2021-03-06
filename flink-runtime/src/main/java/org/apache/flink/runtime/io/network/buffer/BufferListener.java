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

package org.apache.flink.runtime.io.network.buffer;

/**
 * Interface of the availability of buffers. Listeners can opt for a one-time only
 * notification or to be notified repeatedly.
 */
public interface BufferListener {
	/**
	 * Status of the notification result from the buffer listener.
	 */
	enum NotificationResult {
		NONE(false, false),
		BUFFER_USED_FINISHED(true, false),
		BUFFER_USED_NEED_MORE(true, true);

		private final boolean bufferUsed;
		private final boolean needsMoreBuffers;

		NotificationResult(boolean bufferUsed, boolean needsMoreBuffers) {
			this.bufferUsed = bufferUsed;
			this.needsMoreBuffers = needsMoreBuffers;
		}

		/**
		 * Whether the notified buffer is accepted to use by the listener.
		 *
		 * @return <tt>true</tt> if the notified buffer is accepted.
		 */
		boolean bufferUsed() {
			return bufferUsed;
		}

		/**
		 * Whether the listener still needs more buffers to be notified.
		 *
		 * @return <tt>true</tt> if the listener is still waiting for more buffers.
		 */
		boolean needsMoreBuffers() {
			return needsMoreBuffers;
		}
	}

	/**
	 * Notification callback if a buffer is recycled and becomes available in buffer pool.
	 *
	 * @param buffer buffer that becomes available in buffer pool.
	 * @return NotificationResult if the listener wants to be notified next time.
	 */
	NotificationResult notifyBufferAvailable(Buffer buffer);

	/**
	 * Notification callback if the buffer provider is destroyed.
	 */
	void notifyBufferDestroyed();
}
