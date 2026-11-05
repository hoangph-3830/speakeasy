package com.speakeasy.app.data

/** CEFR levels used by topics, stories and path stages, easy to hard. */
object Levels {
    val all = listOf("A1", "A2", "B1", "B2")

    fun name(level: String) = when (level) {
        "A1" -> "Mới bắt đầu"
        "A2" -> "Sơ cấp"
        "B1" -> "Trung cấp"
        "B2" -> "Khá"
        else -> level
    }

    /** Sort key, A1 first; unknown levels go last. */
    fun rank(level: String) = all.indexOf(level).let { if (it < 0) all.size else it }
}

/** A topic on the learning path, with the stage it belongs to (0-based [stageIndex]). */
data class PathStep(val stageIndex: Int, val stage: Stage, val topicId: String)

/** Pure helpers over [Content.path]; [doneCount] gives how many activities a topic has finished. */
object LearningPath {
    private val perTopic = Activity.entries.size

    /** The first topic along the path with an activity left, or null once the whole path is done. */
    fun next(path: List<Stage>, doneCount: (String) -> Int): PathStep? {
        path.forEachIndexed { i, stage ->
            stage.topics.firstOrNull { doneCount(it) < perTopic }?.let { return PathStep(i, stage, it) }
        }
        return null
    }

    fun isComplete(stage: Stage, doneCount: (String) -> Int) = stage.topics.all { doneCount(it) >= perTopic }

    fun topicsDone(stage: Stage, doneCount: (String) -> Int) = stage.topics.count { doneCount(it) >= perTopic }

    /** Finished activities over all activities in the stage, 0 to 1. */
    fun progress(stage: Stage, doneCount: (String) -> Int): Float =
        if (stage.topics.isEmpty()) 0f
        else stage.topics.sumOf { minOf(doneCount(it), perTopic) } / (stage.topics.size * perTopic).toFloat()
}
