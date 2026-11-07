package com.musicfinder.shared.response.payload;

import com.musicfinder.shared.enums.PlayList_Type;

public record PlayListData(Long playlistID, String name, String description, String thumbnailUrl ,PlayList_Type type) {

}
