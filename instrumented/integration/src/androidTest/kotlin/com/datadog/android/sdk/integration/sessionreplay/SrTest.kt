/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.app.Activity
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.MockServerActivityTestRule
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.internal.LazilyParsedNumber
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

internal abstract class SrTest<R : Activity, T : MockServerActivityTestRule<R>> {

    protected abstract fun runInstrumentationScenario(mockServerRule: T): ExpectedSrData

    protected fun verifyExpectedSrData(
        handledRequests: List<HandledRequest>,
        expectedSrData: ExpectedSrData
    ) {
        val records = handledRequests
            .mapNotNull {
                it.extractSrSegmentAsJson()?.asJsonObject
            }
            .flatMap { it.getAsJsonArray("records") }
            .map {
                val asJsonObject = it.asJsonObject
                // we need to remove the timestamp property as `withIgnoringFields` does
                // not work for json objects
                asJsonObject.remove("timestamp")
                asJsonObject
            }
        assertThat(records).usingRecursiveFieldByFieldElementComparator(
            RecursiveComparisonConfiguration
                .builder()
                .withComparatorForType(
                    jsonPrimitivesComparator,
                    JsonPrimitive::class.java
                ).build()
        )
            .containsAll(
                expectedSrData.records.map {
                    val asJsonObject = it.asJsonObject
                    asJsonObject.remove("timestamp")
                    asJsonObject
                }
            )
    }

    private fun HandledRequest.extractSrSegmentAsJson(): JsonElement? {
        val compressedSegmentBody = resolveSrSegmentBodyFromRequest(requestBuffer.clone())
        if (compressedSegmentBody.isNotEmpty()) {
            // decompress the segment binary
            return JsonParser.parseString(String(decompressBytes(compressedSegmentBody)))
        }

        return null
    }

    @Suppress("NestedBlockDepth")
    private fun resolveSrSegmentBodyFromRequest(buffer: Buffer): ByteArray {
        // Example of a multipart form segment body:

        // Content-Disposition: form-data; name="segment"; filename="db081a08-96ab-4931-a98e-b2dd2d9c1b34"
        // Content-Type: application/octet-stream
        // Content-Length: 1060
        // compressed segment as byte array of 1060 length
        var line = buffer.readUtf8Line()
        while (line != null) {
            if (line.lowercase(Locale.ENGLISH).matches(SEGMENT_FORM_DATA_REGEX)) {
                // skip next line
                buffer.readUtf8Line()
                val contentLengthLine = buffer.readUtf8Line()?.lowercase(Locale.ENGLISH)
                if (contentLengthLine != null) {
                    val matcher = CONTENT_LENGTH_REGEX.find(contentLengthLine, 0)
                    if (matcher?.groupValues != null) {
                        // skip the next empty line
                        val contentLength = matcher.groupValues[1].toLong()
                        buffer.readUtf8Line()
                        return buffer.readByteArray(contentLength)
                    }
                }
            }
            line = buffer.readUtf8Line()
        }
        return ByteArray(0)
    }

    private fun decompressBytes(input: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        val decompressor = Inflater()
        decompressor.setInput(input, 0, input.size)
        while (!decompressor.finished()) {
            val resultLength = decompressor.inflate(buf)
            bos.write(buf, 0, resultLength)
        }
        decompressor.end()
        return bos.toByteArray()
    }

    private val jsonPrimitivesComparator: (o1: JsonPrimitive, o2: JsonPrimitive) -> Int =
        { o1, o2 ->
            if (comparingFloatAndLazilyParsedNumber(o1, o2)) {
                // when comparing a float with a LazilyParsedNumber the `JsonPrimitive#equals`
                // method uses Double.parseValue(value) to convert the value from the
                // LazilyParsedNumber and this method uses an extra precision. This will
                // create assertion issues because even though the original values
                // are the same the parsed values are no longer matching.
                if (o1.asString.toDouble() == o2.asString.toDouble()) {
                    0
                } else {
                    -1
                }
            } else {
                if (o1 == o2) {
                    0
                } else {
                    -1
                }
            }
        }

    private fun comparingFloatAndLazilyParsedNumber(o1: JsonPrimitive, o2: JsonPrimitive): Boolean {
        return (o1.isNumber && o2.isNumber) &&
            (o1.asNumber is Float || o2.asNumber is Float) &&
            (o1.asNumber is LazilyParsedNumber || o2.asNumber is LazilyParsedNumber)
    }

    companion object {
        internal val INITIAL_WAIT_MS = TimeUnit.SECONDS.toMillis(60)
        private val SEGMENT_FORM_DATA_REGEX =
            Regex("content-disposition: form-data; name=\"segment\"; filename=\"(.+)\"")
        private val CONTENT_LENGTH_REGEX =
            Regex("content-length: (\\d+)")
    }
}
