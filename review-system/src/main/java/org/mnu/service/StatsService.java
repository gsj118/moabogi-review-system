package org.mnu.service;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.mnu.domain.QueryFilter;
import org.mnu.mapper.StatsMapper;
import org.springframework.stereotype.Service;
/**
 * @brief 회원의 감상 활동 통계와 장르별 평가 결과를 구성합니다.
 * @details 감상 기록 수와 고유 작품 수를 구분하고 미평점은 NULL로 취급합니다.
 * 기간 통계와 회원의 전체 현재 시청 상태는 서로 다른 조회 범위입니다.
 */
@Service
public class StatsService {
    /**
     * @brief 회원별 통계 집계 SQL을 실행하는 Mapper입니다.
     */
    private final StatsMapper mapper;
    /**
     * @brief 통계 집계 저장소를 주입받습니다.
     * @param mapper 회원별 통계 Mapper
     */
    public StatsService(StatsMapper mapper){this.mapper=mapper;}
    /**
     * @brief 조회 조건을 검증하고 활동, 월, 장르, 평점 분포와 상태 통계를 묶습니다.
     * @details 유효 평점 비율은 조건에 맞는 감상 기록 수를 분모로 계산합니다.
     * 장르 평균은 선택 범위 안의 작품별 최신 유효 평점을 사용하고, 높은 평가 장르를 별도로 정렬합니다.
     * 현재 상태 수는 기간과 검색 조건 없이 해당 회원 전체를 조회하며 누락 유형의 건수는 0으로 채웁니다.
     * @param userId 인증된 회원 식별자; 다른 회원 자료와 분리하는 조회 기준
     * @param q 기간과 작품 등의 통계 조회 조건
     * @return summary, months, genres, representatives, distribution, media, states와 화면용 집계 값
     * @throws org.mnu.domain.InputError 조회 조건이 잘못된 경우
     */
    public Map<String,Object> report(long userId,QueryFilter q) {
        q.validate();Map<String,Object> result=new LinkedHashMap<String,Object>();
        Map<String,Object> summary=mapper.summary(userId,q);
        long total=((Number)summary.get("recordCount")).longValue();
        long rated=total-((Number)summary.get("unratedCount")).longValue();
        summary.put("ratedCount",rated);
        summary.put("ratedPercent",ratedPercent(rated,total));
        result.put("summary",summary);result.put("months",mapper.months(userId,q));result.put("genres",mapper.genres(userId,q));
        result.put("representatives",mapper.representatives(userId,q));result.put("distribution",mapper.distribution(userId,q));result.put("media",mapper.media(userId,q));result.put("states",mapper.states(userId));
        @SuppressWarnings("unchecked") List<Map<String,Object>> high=new ArrayList<Map<String,Object>>((List<Map<String,Object>>)result.get("genres"));
        high.sort((a,b)->{java.math.BigDecimal av=(java.math.BigDecimal)a.get("averageRating"),bv=(java.math.BigDecimal)b.get("averageRating");if(av==null)return bv==null?0:1;if(bv==null)return -1;return bv.compareTo(av);});
        result.put("highRatedGenres",high);
        @SuppressWarnings("unchecked") List<Map<String,Object>> states=(List<Map<String,Object>>)result.get("states");
        @SuppressWarnings("unchecked") List<Map<String,Object>> media=(List<Map<String,Object>>)result.get("media");
        Map<String,Long> counts=new LinkedHashMap<String,Long>();for(String status:Arrays.asList("WANT","WATCHING","COMPLETED","DROPPED"))counts.put(status,0L);for(Map<String,Object> row:states)counts.put((String)row.get("status"),((Number)row.get("workCount")).longValue());result.put("stateCounts",counts);
        Map<String,Long> mediaCounts=new LinkedHashMap<String,Long>();mediaCounts.put("MOVIE",0L);mediaCounts.put("TV",0L);for(Map<String,Object> row:media)mediaCounts.put((String)row.get("mediaType"),((Number)row.get("recordCount")).longValue());result.put("mediaCounts",mediaCounts);return result;
    }
    /**
     * @brief 유효 평점 기록의 비율을 0~100 단위의 소수 한 자리로 계산합니다.
     * @details HALF_UP으로 반올림하며 감상 기록이 0건이면 비율 없음인 NULL을 반환합니다.
     * @param rated 평점이 있는 감상 기록 수
     * @param total 같은 조회 범위의 전체 감상 기록 수
     * @return 백분율 값; 분모가 0이면 null
     * @throws IllegalArgumentException 건수가 음수이거나 유효 평점 건수가 전체 건수보다 큰 경우
     */
    public static BigDecimal ratedPercent(long rated,long total) {
        if(total<0 || rated<0 || rated>total)throw new IllegalArgumentException("Invalid rating counts");
        return total==0?null:BigDecimal.valueOf(rated).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),1,RoundingMode.HALF_UP);
    }
}
