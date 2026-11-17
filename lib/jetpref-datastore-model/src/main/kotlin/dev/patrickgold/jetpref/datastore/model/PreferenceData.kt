/*
 * Copyright 2025 Patrick Goldinger
 * Modified 2026 by the SwiftFloris Contributors: expect and actual merged into one file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.jetpref.datastore.model

import dev.patrickgold.jetpref.datastore.annotations.PreferenceKey
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Interface for an implementation of a preference data accessor for
 * type [V]. A preference data is the heart of all the jetpref logic
 * and allows to observe value changes. The default implementation and
 * behavior internally is very similar to Android's LiveData, but this
 * interface allows for any kind of custom implementation.
 *
 * @since 0.1.0
 */
interface PreferenceData<V : Any> {
    /**
     * The type of this preference, useful especially in the serialization
     * process. If the preference type attribute is invalid, this preference
     * data will not work correctly.
     *
     * @since 0.1.0
     */
    val type: PreferenceType

    /**
     * The serializer for this preference data, used by the serialization
     * process to persist the cached preferences onto the device storage.
     *
     * @since 0.1.0
     */
    val serializer: PreferenceSerializer<V>

    /**
     * The key of this value. Generally a key must only contain the following
     * characters and symbols:
     *  - `a-z`
     *  - `A-Z`
     *  - `0-9` (not at the start though)
     *  - `_`
     *  - `-` (not at the start or end though)
     *
     * @since 0.1.0
     */
    @PreferenceKey val key: String

    /**
     * The typed key of this preference data. Is a composite of [type] and [key].
     *
     * @since 0.3.0
     */
    val typedKey: PreferenceModel.TypedKey

    /**
     * The default value for this preference data. Is used if no valid persisted
     * value is existent or when resetting this preference data.
     *
     * @since 0.1.0
     */
    val default: V

    /**
     * Gets the cached value of this preference data.
     *
     * @return The value of this preference data or [default].
     *
     * @since 0.1.0
     */
    fun get(): V

    /**
     * Gets the cached value of this preference data.
     *
     * @return The value of this preference data or null.
     *
     * @since 0.1.0
     */
    fun getOrNull(): V?

    /**
     * Returns a state flow for this preference data, allowing to collect or observe the value.
     *
     * @since 0.3.0
     */
    fun asFlow(): StateFlow<V>

    /**
     * Sets the value of this preference data.
     *
     * @param value The new value to set for this preference data.
     *
     * @since 0.1.0
     */
    suspend fun set(value: V): Result<Unit>

    /**
     * Resets this preference data to [default].
     *
     * @since 0.1.0
     */
    suspend fun reset(): Result<Unit>

    suspend fun init(value: V?, handler: ValuePersistHandler<V>)

    fun interface ValuePersistHandler<V> {
        /**
         * Queues the save of [newValue] and returns a function that waits for its result.
         *
         * This is called while the value's write guard is held, so it must only queue the save and
         * never wait for it: a store load sets every value under that same guard from the store's
         * one worker, and waiting on the worker while holding the guard would deadlock both.
         */
        fun onValueChanged(newValue: V?): suspend () -> Result<Unit>
    }
}

private class PreferenceDataImpl<V : Any>(
    override val key: String,
    override val default: V,
    override val type: PreferenceType,
    override val serializer: PreferenceSerializer<V>,
) : PreferenceData<V> {
    override val typedKey = PreferenceModel.TypedKey(type, key)
    private val cachedValue = AtomicReference<V?>(null)
    private var cachedValueFlow = MutableStateFlow(default)
    private val cachedValueWriteGuard = Mutex()
    private var valuePersistHandler: PreferenceData.ValuePersistHandler<V>? = null

    init {
        Validator.validateKey(key)
    }

    override fun get(): V = cachedValue.get() ?: default

    override fun getOrNull(): V? = cachedValue.get()

    override fun asFlow() = cachedValueFlow.asStateFlow()

    // The value changes and its save is queued under the guard, so saves keep call order. The
    // wait for the save happens after the guard is released (see ValuePersistHandler).
    override suspend fun set(value: V): Result<Unit> {
        val awaitSaved = cachedValueWriteGuard.withLock {
            cachedValue.set(value)
            cachedValueFlow.value = value
            valuePersistHandler?.onValueChanged(value)
        }
        return awaitSaved?.invoke() ?: Result.success(Unit)
    }

    override suspend fun reset(): Result<Unit> {
        val awaitSaved = cachedValueWriteGuard.withLock {
            cachedValue.set(null)
            cachedValueFlow.value = default
            valuePersistHandler?.onValueChanged(null)
        }
        return awaitSaved?.invoke() ?: Result.success(Unit)
    }

    override suspend fun init(
        value: V?,
        handler: PreferenceData.ValuePersistHandler<V>,
    ): Unit = cachedValueWriteGuard.withLock {
        cachedValue.set(value)
        cachedValueFlow.value = value ?: default
        valuePersistHandler = handler
    }
}

internal fun <V : Any> preferenceDataOf(
    key: String,
    default: V,
    type: PreferenceType,
    serializer: PreferenceSerializer<V>
): PreferenceData<V> {
    return PreferenceDataImpl(key, default, type, serializer)
}
