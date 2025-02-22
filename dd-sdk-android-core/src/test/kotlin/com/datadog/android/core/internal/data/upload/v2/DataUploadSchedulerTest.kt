/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload.v2

import com.datadog.android.core.configuration.UploadFrequency
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DataUploadSchedulerTest {

    lateinit var testedScheduler: DataUploadScheduler

    @Mock
    lateinit var mockExecutor: ScheduledThreadPoolExecutor

    @Forgery
    lateinit var fakeUploadFrequency: UploadFrequency

    @BeforeEach
    fun `set up`() {
        testedScheduler = DataUploadScheduler(
            mock(),
            mock(),
            mock(),
            mock(),
            mock(),
            fakeUploadFrequency,
            mockExecutor,
            mock()
        )
    }

    @Test
    fun `when start it will schedule a runnable`() {
        // When
        testedScheduler.startScheduling()

        // Then
        verify(mockExecutor).schedule(
            any(),
            eq(fakeUploadFrequency.baseStepMs * DataUploadRunnable.DEFAULT_DELAY_FACTOR),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `when stop it will try to remove the scheduled runnable`() {
        // Given
        testedScheduler.startScheduling()

        // When
        testedScheduler.stopScheduling()

        // Then
        val argumentCaptor = argumentCaptor<Runnable>()
        verify(mockExecutor).schedule(
            argumentCaptor.capture(),
            eq(fakeUploadFrequency.baseStepMs * DataUploadRunnable.DEFAULT_DELAY_FACTOR),
            eq(TimeUnit.MILLISECONDS)
        )
        verify(mockExecutor).remove(argumentCaptor.firstValue)
    }
}
