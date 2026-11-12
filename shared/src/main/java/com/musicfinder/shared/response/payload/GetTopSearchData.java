package com.musicfinder.shared.response.payload;

import java.util.List;

public record GetTopSearchData(List<KeywordStatisticData> lists,String type) {

}
