package com.musicfinder.server.repository;

import com.musicfinder.server.config.DatabaseConfig;
import com.musicfinder.server.util.DataException;
import com.musicfinder.server.model.SearchStatistics;

import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SearchStatisticsRepository {
    private SearchStatistics mapResultSetToSearchStatistics(ResultSet rs) throws SQLException {
        Timestamp lastSearchedAt = rs.getTimestamp("last_searched_at");
        return SearchStatistics.builder()
                .statisticsId(rs.getLong("statistic_id"))
                .keyword(rs.getString("keyword"))
                .normalizedKeyword(rs.getString("normalized_keyword"))
                .searchType(rs.getString("search_type"))
                .searchCount(rs.getLong("search_count"))
                .lastSearchedAt(lastSearchedAt != null ? lastSearchedAt.toLocalDateTime() : null)
                .build();
    }

    // Thống kê bài hát và ca sĩ tìm kiếm nhiều nhất
    // Truyền Type nếu có
    public List<SearchStatistics> findTopKeywords(String searchType, int limit) {
        List<SearchStatistics> list = new ArrayList<>();
        boolean hadType = (searchType != null && !searchType.isBlank());
        String sql = "SELECT * FROM SEARCH_STATISTICS "
                + (hadType ? "WHERE search_type = ? " : "")
                + "ORDER BY search_count DESC, last_searched_at DESC LIMIT ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
              int Idx = 1;
            if (hadType) {
                ps.setString(Idx++, searchType);
            }
            ps.setInt(Idx, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToSearchStatistics(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            log.error("Lỗi lấy top từ khóa tìm kiếm: {}", e.getMessage());
            throw new DataException("Lỗi lấy top từ khóa tìm kiếm", e);
        }
    }

    // Lưu từ khoá
    public SearchStatistics save(SearchStatistics stat) {
        String sql = """
                    INSERT INTO SEARCH_STATISTICS (keyword, normalized_keyword, search_type, search_count, last_searched_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (normalized_keyword, search_type)
                    DO UPDATE SET search_count = SEARCH_STATISTICS.search_count + 1, last_searched_at = CURRENT_TIMESTAMP
                """;
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, stat.getKeyword());
            ps.setString(2, stat.getNormalizedKeyword());
            ps.setString(3, stat.getSearchType());
            ps.setLong(4, stat.getSearchCount() != null ? stat.getSearchCount() : 1L);
            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0) {
                throw new DataException("Thêm thống kê từ khóa thất bại, không có dòng nào được thêm.");
            }
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    stat.setStatisticsId(generatedKeys.getLong(1));
                }
            }
            return stat;
        } catch (SQLException e) {
            log.error("Lỗi khi thêm mới thống kê từ khóa: {}", e.getMessage(), e);
            throw new DataException("Lỗi lưu thống kê từ khóa", e);
        }
    }

    // GHi nhận lượt tìm kiếm
    public boolean updateSearchCount(String normalizedKeyword, String searchType) {
        String sql = """
                    UPDATE SEARCH_STATISTICS
                    SET search_count = search_count + 1,
                        last_searched_at = CURRENT_TIMESTAMP
                    WHERE normalized_keyword = ? AND search_type = ?
                """;
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, normalizedKeyword);
            ps.setString(2, searchType);
            int affectedRows = ps.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            log.error("Lỗi khi tăng search_count từ khóa [{} - {}]: {}", normalizedKeyword, searchType, e.getMessage(),
                    e);
            throw new DataException("Lỗi tăng search_count từ khóa", e);
        }
    }
}
