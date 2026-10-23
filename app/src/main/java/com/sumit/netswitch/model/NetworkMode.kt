package com.sumit.netswitch.model

enum class NetworkMode(val displayName: String) {
    FIVE_G("5G"),
    FOUR_G("4G"),
    UNKNOWN("Unknown");

    fun getOpposite(): NetworkMode {
        return when (this) {
            FIVE_G -> FOUR_G
            FOUR_G -> FIVE_G
            UNKNOWN -> FOUR_G
        }
    }
}
