package com.zack.focus

object MotivationMessages {

    private val messages = listOf(
        "You are not lazy. Your attention is valuable.",
        "One small pause can change the whole day.",
        "You chose Focus because part of you wants better.",
        "Your future self will thank you for this moment.",
        "You do not need to win the whole day. Just win this decision.",
        "The app can wait. Your goals matter more.",
        "Boredom is a door. What is on the other side?",
        "Strength is not avoiding temptation. It is choosing differently.",
        "Every scroll you skip is a moment you reclaim.",
        "You are more than your habits. You can change them.",
        "This pause is the intervention.",
        "Small choices compound. This one matters."
    )

    fun getRandom(): String = messages.random()
}
