package ai.typesafe.kotlin

import ai.typesafe.TypeSafeClient
import ai.typesafe.model.ChoiceAnswer
import ai.typesafe.model.ChoiceQuestion
import ai.typesafe.model.EvaluationRequest
import ai.typesafe.model.NoulAnswer
import ai.typesafe.model.NoulQuestion
import ai.typesafe.model.ScoreAnswer
import ai.typesafe.model.ScoreQuestion
import ai.typesafe.model.SystemOneResult

/**
 * Idiomatic Kotlin layer over the Java SDK.
 *
 * ```kotlin
 * val client = TypeSafeClient.fromEnv()
 *
 * val result = client.evaluate("Help! My payouts have been failing for 3 days.") {
 *     noul("is_urgent", "Does this convey urgency?")
 *     choice("department", "Which team should handle this?") {
 *         "billing" to "Payments, invoicing, refunds"
 *         "technical" to "Bugs, outages, integrations"
 *     }
 *     score("frustration", "How frustrated is the customer?") {
 *         level("Calm")
 *         level("Frustrated")
 *         level("Very angry")
 *     }
 * }
 *
 * result.noulOrNull("is_urgent")?.takeIf { it.isYes(0.7) } ?: return
 * val dept = result.choice("department")
 * if (dept.confidenceOrDefault < 0.5) escalateToHuman()
 * ```
 */

// -- Request DSL ----------------------------------------------------------

/** DSL entry: builds an [EvaluationRequest] from a state string or JSON value. */
fun evaluationRequest(state: Any, block: RequestBuilder.() -> Unit = {}): EvaluationRequest =
    RequestBuilder(state).apply(block).build()

class RequestBuilder internal constructor(private val state: Any) {
    internal val questions = linkedMapOf<String, ai.typesafe.model.Question>()

    var model: String? = null
        private set

    fun model(value: String) {
        model = value
    }

    fun question(question: ai.typesafe.model.Question) {
        questions[question.id()] = question
    }

    fun noul(id: String, instructions: String) {
        questions[id] = NoulQuestion.of(id, instructions)
    }

    fun noul(id: String, instructions: String, trueMeans: String, falseMeans: String) {
        questions[id] = NoulQuestion.of(id, instructions, trueMeans, falseMeans)
    }

    fun choice(id: String, instructions: String, options: OptionScope.() -> Unit) {
        questions[id] = ChoiceQuestion.of(id, instructions, OptionScope().apply(options).build())
    }

    fun score(id: String, instructions: String, levels: LevelScope.() -> Unit) {
        questions[id] = ScoreQuestion.of(id, instructions, LevelScope().apply(levels).build())
    }

    internal fun build(): EvaluationRequest =
        EvaluationRequest(state, model, questions)
}

/** Receiver for `choice` DSL: declare options as pairs. */
class OptionScope internal constructor() {
    private val options = linkedMapOf<String, String>()

    infix fun String.to(description: String) {
        options[this] = description
    }

    operator fun String.unaryPlus() {
        options[this] = ""
    }

    internal fun build(): Map<String, String> = options
}

/** Receiver for `score` DSL: declare levels in order. */
class LevelScope internal constructor() {
    private val levels = mutableListOf<String>()

    fun level(description: String) {
        levels += description
    }

    internal fun build(): List<String> = levels
}

// -- Client convenience ---------------------------------------------------

/** One-call evaluation from a state plus a request DSL block. */
fun TypeSafeClient.evaluate(state: Any, block: RequestBuilder.() -> Unit): SystemOneResult {
    val builder = RequestBuilder(state).apply(block)
    val request = builder.build()
    val model = builder.model
    return evaluate(if (model != null) request.withModel(model) else request)
}

// -- Answer accessors -----------------------------------------------------

/** Kotlin-friendly confidence: 0.0 when the API omits it. */
val ChoiceAnswer.confidenceOrDefault: Double
    get() = confidence ?: 0.0

val ScoreAnswer.confidenceOrDefault: Double
    get() = confidence ?: 0.0

/** Nullable noul lookup: null when the id is absent or of a different type. */
fun SystemOneResult.noulOrNull(id: String): NoulAnswer? = answers[id] as? NoulAnswer

fun SystemOneResult.choiceOrNull(id: String): ChoiceAnswer? = answers[id] as? ChoiceAnswer

fun SystemOneResult.scoreOrNull(id: String): ScoreAnswer? = answers[id] as? ScoreAnswer

/** True when the noul answer exists and meets the threshold; false otherwise. */
fun SystemOneResult.isYes(id: String, threshold: Double = 0.5): Boolean =
    noulOrNull(id)?.isYes(threshold) == true

/** Run [block] only when the choice answer's confidence meets [minConfidence]. */
inline fun SystemOneResult.onConfidentChoice(id: String, minConfidence: Double = 0.5, block: (ChoiceAnswer) -> Unit) {
    choiceOrNull(id)?.takeIf { it.confidenceOrDefault >= minConfidence }?.let(block)
}

private fun EvaluationRequest.withModel(model: String): EvaluationRequest =
    EvaluationRequest(state, model, questions)
