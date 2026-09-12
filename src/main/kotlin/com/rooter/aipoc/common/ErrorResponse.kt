package com.rooter.aipoc.common

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val code: String, val message: String)
