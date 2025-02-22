/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.event

import com.datadog.android.event.EventMapper
import com.datadog.android.rum.model.ViewEvent

/**
 * An interface which can be implemented to modify the writable attributes inside ViewEvent.
 */
interface ViewEventMapper : EventMapper<ViewEvent> {
    /**
     * By implementing this method you can intercept and modify the writable
     * attributes inside any event [ViewEvent] before it gets serialised.
     *
     * @param event the event to be serialised
     * @return the modified event [ViewEvent]
     *
     */
    override fun map(event: ViewEvent): ViewEvent
}
