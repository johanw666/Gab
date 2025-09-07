package com.keylesspalace.tusky.json

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalStdlibApi::class)
class StringOrBooleanAdapterTest {

    private val moshi = Moshi.Builder()
        .add(StringOrBooleanAdapter.ANNOTATION_FACTORY)
        .build()

    @Test
    fun `should deserialize class when nullable boolean is missing`() {
        val jsonInput = """
            {
              "someBoolean": true
            }
        """.trimIndent()

        assertEquals(
            TestClass(
                someBoolean = true,
                someNullableBoolean = null
            ),
            moshi.adapter<TestClass>().fromJson(jsonInput)
        )
    }

    @Test
    fun `should deserialize class when nullable boolean is null`() {
        val jsonInput = """
            {
              "someBoolean": false,
              "someNullableBoolean": null
            }
        """.trimIndent()

        assertEquals(
            TestClass(
                someBoolean = false,
                someNullableBoolean = null
            ),
            moshi.adapter<TestClass>().fromJson(jsonInput)
        )
    }

    @Test
    fun `should deserialize class when string is 'false'`() {
        val jsonInput = """
            {
              "someBoolean": "false",
              "someNullableBoolean": true
            }
        """.trimIndent()

        assertEquals(
            TestClass(
                someBoolean = false,
                someNullableBoolean = true
            ),
            moshi.adapter<TestClass>().fromJson(jsonInput)
        )
    }

    @Test
    fun `should deserialize class when string is 'true'`() {
        val jsonInput = """
            {
              "someBoolean": "true",
              "someNullableBoolean": "true"
            }
        """.trimIndent()

        assertEquals(
            TestClass(
                someBoolean = true,
                someNullableBoolean = true
            ),
            moshi.adapter<TestClass>().fromJson(jsonInput)
        )
    }

    @Test
    fun `should deserialize class when string has any content`() {
        val jsonInput = """
            {
              "someBoolean": "something",
              "someNullableBoolean": "false"
            }
        """.trimIndent()

        assertEquals(
            TestClass(
                someBoolean = false,
                someNullableBoolean = false
            ),
            moshi.adapter<TestClass>().fromJson(jsonInput)
        )
    }

    @JsonClass(generateAdapter = true)
    data class TestClass(
        @StringOrBoolean val someBoolean: Boolean,
        @StringOrBoolean val someNullableBoolean: Boolean? = null
    )
}
