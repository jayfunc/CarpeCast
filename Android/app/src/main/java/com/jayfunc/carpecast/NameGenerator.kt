package com.jayfunc.carpecast

object NameGenerator {
    private val adjectives = listOf(
        "Clever", "Fast", "Brave", "Silent", "Mighty", "Quick", "Happy",
        "Bright", "Cool", "Calm", "Fierce", "Gentle", "Lucky", "Proud"
    )

    private val nouns = listOf(
        "Fox", "Tiger", "Bear", "Eagle", "Wolf", "Lion", "Hawk",
        "Owl", "Panda", "Shark", "Falcon", "Dolphin", "Panther", "Leopard"
    )

    fun generate(): String {
        val adj = adjectives.random()
        val noun = nouns.random()
        return "$adj-$noun"
    }
}
