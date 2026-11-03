package org.mnu;

import static org.junit.Assert.*;
import java.math.BigDecimal;
import org.junit.Test;
import org.mnu.service.StatsService;
import org.mnu.domain.QueryFilter;

public class StatsServiceTest {
    @Test public void percentageHasExplicitPrecisionAndEmptyDenominator() {
        long[][] cases={{0,0},{0,4},{4,4},{1,4},{2,4},{2,3},{3,4},{3,17}};
        String[] expected={null,"0.0","100.0","25.0","50.0","66.7","75.0","17.6"};
        for(int i=0;i<cases.length;i++)assertEquals(expected[i]==null?null:new BigDecimal(expected[i]),StatsService.ratedPercent(cases[i][0],cases[i][1]));
    }
    @Test public void pageInputsNormalizeBeforeBoundedOffset() {
        assertEquals(1,QueryFilter.parsePage("abc"));assertEquals(1,QueryFilter.parsePage("0"));assertEquals(1,QueryFilter.parsePage("-5"));
        QueryFilter q=new QueryFilter();q.setPage(QueryFilter.parsePage("999999999999999999999"));q.clampPage(16);assertEquals(2,q.getPage());assertEquals(15L,q.getOffset());q.clampPage(0);assertEquals(1,q.getPage());
    }
}
