package com.musicfinder.shared.request.payload;

import java.util.List;

import com.musicfinder.shared.response.payload.SongData;

public record Add_To_PlaylistPayload(List<Long> playlistId, SongData song) {

}
