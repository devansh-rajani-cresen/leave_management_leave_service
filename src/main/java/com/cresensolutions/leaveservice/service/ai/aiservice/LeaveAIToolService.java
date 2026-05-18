package com.cresensolutions.leaveservice.service.ai;

import com.cresensolutions.leaveservice.repository.ApplyLeaveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveAIToolService {

    private final ApplyLeaveRepository applyLeaveRepository;

    @Tool(
            name = "getLeaveCount",
            description = "Returns total number of leaves of a user"
    )
    public String getLeaveCount(Long userId){
        long count = applyLeaveRepository.countByUserId(userId);
        log.info("Total Leaves count for userId : {} is {}", userId, count);

        if (count == 0){
            return "You haven't applied any leaves yet!";
        }

        return "Your applied leave count is : " + count;
    }
}
