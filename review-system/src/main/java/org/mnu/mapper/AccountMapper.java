package org.mnu.mapper;
import java.util.Map;
public interface AccountMapper {
    Map<String,Object> byUsername(String username);
    int insert(Map<String,Object> account);
}
