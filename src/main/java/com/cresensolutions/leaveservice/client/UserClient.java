// Internal AI APIs for chatbot tool calling

package com.cresensolutions.leaveservice.client;

import com.cresensolutions.leaveservice.config.FeignConfig;
import com.cresensolutions.leaveservice.dto.aidto.BasicUserInfoForAI;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@FeignClient(
        name = "userservice",
        configuration = FeignConfig.class
)
public interface UserClient {

    @GetMapping("/internal/users/count")
    Long getEmployeeCount();

    @GetMapping("/internal/users/role/{role}")
    List<BasicUserInfoForAI> getUserByRole(@PathVariable String role);

    @GetMapping("/internal/users/search")
    List<BasicUserInfoForAI> searchUsers(@RequestParam String name);
}
