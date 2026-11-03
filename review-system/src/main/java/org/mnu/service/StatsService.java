package org.mnu.service;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.mnu.domain.QueryFilter;
import org.mnu.mapper.StatsMapper;
import org.springframework.stereotype.Service;
@Service
public class StatsService {
    private final StatsMapper mapper;
    public StatsService(StatsMapper mapper){this.mapper=mapper;}
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
    /** Percentage in the 0..100 unit. A null result denotes an empty denominator. */
    public static BigDecimal ratedPercent(long rated,long total) {
        if(total<0 || rated<0 || rated>total)throw new IllegalArgumentException("Invalid rating counts");
        return total==0?null:BigDecimal.valueOf(rated).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),1,RoundingMode.HALF_UP);
    }
}
