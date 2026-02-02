package com.breuninger.reco.richrelevanceprodukte.ports.kafka

enum class FeedMethodHeader {
    PUT,
    DELETE,
    ;

    companion object {
        const val METHOD_HEADER_KEY = "HTTP-Method"
    }
}
