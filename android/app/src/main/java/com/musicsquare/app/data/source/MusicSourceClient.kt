package com.musicsquare.app.data.source

import com.musicsquare.app.data.model.Track

interface MusicSourceClient {
    suspend fun search(keyword: String, limit: Int, page: Int = 1): List<Track>
    suspend fun fetchDetails(track: Track): Track
}
