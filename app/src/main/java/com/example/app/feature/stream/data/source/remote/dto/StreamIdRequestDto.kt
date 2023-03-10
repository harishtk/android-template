package com.example.app.feature.stream.data.source.remote.dto

import com.google.gson.annotations.SerializedName
import com.example.app.feature.stream.domain.model.request.StreamIdRequest

data class StreamIdRequestDto(
    @SerializedName("id")
    val id: String
)

fun StreamIdRequest.asDto(): StreamIdRequestDto {
    return StreamIdRequestDto(
        id = id
    )
}