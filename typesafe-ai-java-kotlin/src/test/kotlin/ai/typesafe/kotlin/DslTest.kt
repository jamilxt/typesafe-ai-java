package ai.typesafe.kotlin

import ai.typesafe.TypeSafeClient
import ai.typesafe.http.RetryPolicy
import ai.typesafe.http.Transport
import ai.typesafe.model.SystemOneResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val OK_BODY = """
{
  "model": "jev-1.13.0",
  "answers": {
    "is_urgent": { "type": "noul", "noul": 0.95 },
    "department": { "type": "choice", "choice": "billing",
      "probabilities": {"billing": 0.88, "technical": 0.12},
      "confidence": 0.81 },
    "frustration": { "type": "score", "score": 1.05,
      "legend": {"0": "Calm", "1": "Frustrated", "2": "Very angry"},
      "probabilities": {"0": 0.0, "1": 0.95, "2": 0.05},
      "confidence": 0.92 }
  },
  "usage": { "input_tokens": 318, "output_tokens": 34 }
}
""".trimIndent()

private fun fakeClient(body: String = OK_BODY): TypeSafeClient =
    TypeSafeClient.builder("test-key")
        .transport { _, _, _, _ -> Transport.Response(200, mapOf(), body) }
        .retryPolicy(RetryPolicy.none())
        .build()

class DslTest {

    @Test
    fun dslBuildsAllThreePrimitives() {
        val request = evaluationRequest("state text") {
            noul("urgent", "Does this convey urgency?", "Time-sensitive", "No urgency")
            choice("dept", "Which team?") {
                "billing" to "Payments"
                "technical" to "Bugs"
                +"other"
            }
            score("frustration", "How angry?") {
                level("Calm")
                level("Frustrated")
                level("Angry")
            }
        }

        assertEquals(3, request.questions.size)
        val json = ai.typesafe.json.Json.writeRequest(request)
        assertTrue("\"type\":\"noul\"" in json)
        assertTrue("\"type\":\"choice\"" in json)
        assertTrue("\"type\":\"score\"" in json)
        assertTrue("\"other\":\"\"" in json)
    }

    @Test
    fun clientEvaluateExtensionRunsEndToEnd() {
        val result = fakeClient().evaluate("Help! Payouts failing 3 days.") {
            noul("is_urgent", "Does this convey urgency?")
            choice("department", "Which team?") {
                "billing" to "Payments"
                "technical" to "Bugs"
            }
            score("frustration", "How angry?") {
                level("Calm"); level("Frustrated"); level("Very angry")
            }
        }

        assertTrue(result.isYes("is_urgent", 0.7))
        assertEquals("billing", result.choiceOrNull("department")?.choice)
        assertEquals(0.81, result.choiceOrNull("department")?.confidenceOrDefault)
        assertEquals(1.05, result.scoreOrNull("frustration")?.score)
        assertEquals(318, result.usage.inputTokens)
    }

    @Test
    fun nullableAccessorsReturnNullOnWrongTypeOrMissing() {
        val result = fakeClient().evaluate("state") {
            noul("q", "Is this true?")
        }
        assertNull(result.choiceOrNull("q"))
        assertNull(result.noulOrNull("missing"))
        assertFalse(result.isYes("missing", 0.5))
    }

    @Test
    fun onConfidentChoiceOnlyFiresAboveThreshold() {
        var fired = false
        fakeClient().evaluate("state") {
            choice("department", "Which team?") {
                "billing" to "Payments"
            }
        }.onConfidentChoice("department", minConfidence = 0.5) {
            fired = true
        }
        assertTrue(fired)

        var notFired = true
        fakeClient().evaluate("state") {
            choice("department", "Which team?") {
                "billing" to "Payments"
            }
        }.onConfidentChoice("department", minConfidence = 0.99) {
            notFired = false
        }
        assertTrue(notFired)
    }

    @Test
    fun modelOverrideReachesRequest() {
        val captured = mutableListOf<String>()
        val client = TypeSafeClient.builder("test-key")
            .transport { _, _, body, _ ->
                captured += body
                Transport.Response(200, mapOf(), OK_BODY)
            }
            .retryPolicy(RetryPolicy.none())
            .build()

        client.evaluate("state") {
            model("jev-1.13.0")
            noul("q", "True?")
        }

        assertTrue("\"model\":\"jev-1.13.0\"" in captured[0])
    }
}
