package com.speakeasy.app.data

/** CEFR levels used by topics, stories and path stages, easy to hard. */
object Levels {
    val all = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    fun name(level: String) = when (level) {
        "A1" -> "Mới bắt đầu"
        "A2" -> "Sơ cấp"
        "B1" -> "Trung cấp"
        "B2" -> "Khá"
        "C1" -> "Cao cấp"
        "C2" -> "Thành thạo"
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

    /**
     * Stages at or below the level passed in the placement test are skipped by "continue learning".
     * Placing at C2 skips nothing: there is no harder stage to send the learner to.
     */
    fun skippedStage(stage: Stage, placed: String?): Boolean =
        !placed.isNullOrEmpty() && placed != "C2" && Levels.all.indexOf(stage.level) <= Levels.all.indexOf(placed)

    /**
     * The first topic along the path with an activity left, or null once the whole path is done.
     * First the stages the learner was placed into, then anything left unfinished below them.
     */
    fun next(path: List<Stage>, doneCount: (String) -> Int, placed: String?): PathStep? {
        for (skipPlaced in listOf(true, false)) {
            path.forEachIndexed { i, stage ->
                if (skipPlaced && skippedStage(stage, placed)) return@forEachIndexed
                stage.topics.firstOrNull { doneCount(it) < perTopic }?.let { return PathStep(i, stage, it) }
            }
        }
        return null
    }

    const val KIDS = "kids"

    /**
     * The learning path for who is learning: children get the kids' stages first and then the general
     * path; adults (or anyone else) never see the kids' stages.
     */
    fun visible(path: List<Stage>, audience: String?): List<Stage> {
        val (kids, general) = path.partition { it.audience == KIDS }
        return if (audience == KIDS) kids + general else general
    }

    /** [next] with no placement test taken. */
    fun next(path: List<Stage>, doneCount: (String) -> Int): PathStep? = next(path, doneCount, null)

    fun isComplete(stage: Stage, doneCount: (String) -> Int) = stage.topics.all { doneCount(it) >= perTopic }

    fun topicsDone(stage: Stage, doneCount: (String) -> Int) = stage.topics.count { doneCount(it) >= perTopic }

    /** Finished activities over all activities in the stage, 0 to 1. */
    fun progress(stage: Stage, doneCount: (String) -> Int): Float =
        if (stage.topics.isEmpty()) 0f
        else stage.topics.sumOf { minOf(doneCount(it), perTopic) } / (stage.topics.size * perTopic).toFloat()
}
