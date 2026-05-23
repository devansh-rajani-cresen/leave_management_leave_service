// In this file, I have defined constants which I have to use in Flowable

package com.cresensolutions.leaveservice.common;

public class LeaveConstants {
    private LeaveConstants() {}

    // Authorization
    public static final String AUTH_HEADER = "Authorization";
    public static final String HEADER_STARTING = "Bearer ";
    public static final Integer TOKEN_STARTING_INDEX = 7;

    // Roles
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MANAGER = "MANAGER";
    public static final String ROLE_EMPLOYEE = "EMPLOYEE";
    public static final String ROLE_HR = "HR";

    // Variables for trail (for Audit trail)
    public static final String ROLE = "role";
    public static final String ACTION = "action";
    public static final String ACTION_LEAVE_APPLIED = "Applied";
    public static final String ACTION_MANAGER_APPLIED = "Manager Applied";
    public static final String TIME = "time";
    public static final String TRAIL = "trail";
    public static final String REASON = "reason";
    public static final String IS_CURRENT_STEP = "isCurrent";
    public static final String ACTOR_NAME = "actorName";
    public static final String MANAGER_NAME = "managerName";

    public static final String ACTION_MANAGER_APPROVED = "Approved";
    public static final String ACTION_MANAGER_REJECTED = "Rejected";
    public static final String ACTION_MANAGER_PARTIAL_APPROVED = "Partially Approved";

    public static final String ACTION_HR_APPROVED = "Approved";
    public static final String ACTION_HR_REJECTED = "Rejected";
    public static final String ACTION_HR_PARTIAL_APPROVED = "Partially Approved";

    // BPMN & Process Variables
    public static final String USER_ID = "userId";
    public static final String USER_FULL_NAME = "fullName";
    public static final String STATUS_PENDING = "PENDING";
    public static final String LEAVE_APPROVAL_FLOW_PROCESS_ID = "leaveProcess";
    public static final String LEAVE_ID = "leaveId";
    public static final String MANAGER_EMAIL = "managerEmail";
    public static final String EMPLOYEE_EMAIL = "employeeEmail";
    public static final String LEAVE_TYPE = "leaveType";
    public static final String FROM_DATE = "fromDate";
    public static final String TO_DATE = "toDate";
    public static final String EMP_LEAVE_REASON = "reason";
    public static final String REJECTION_REASON = "rejectionReason";

    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_DELETED = "DELETED";
    public static final String APPROVED_BY_HR = "HR";

    public static final String HR_EMAIL = "hrEmail";
    public static final String SKIP_MANAGER_APPROVAL = "skipManagerApproval";
    public static final String TRIGGER_MAIL_DATE = "TRIGGER_MAIL_DATE";
    public static final String DEFAULT_HR_EMAIL = "devansh4working@gmail.com";
    public static final String TASK_ID = "taskId";
    public static final String LEAVE_DAY_DETAILS = "dayDetails";
    public static final String EMPLOYEE_NAME = "employeeName";
    public static final String DAY = "date";
    public static final String DAY_TYPE = "type";
    public static final String HALF_DAY_SESSION = "session";
    public static final String APPROVED = "approved";
    public static final String MANAGER_APPROVED = "managerApproved";
    public static final String HR_APPROVED = "hrApproved";

    // ── Partial-approval (manager approves a subset of days) ──────────────────
    /**
     * Key in the manager-action request payload that carries the list of
     * LeaveDayDetails IDs the manager has chosen to approve.
     * If absent / null  → treat the whole leave as fully approved or rejected
     *                       based on MANAGER_APPROVED flag.
     * If present        → partial approval: approved-day IDs listed here,
     *                       remaining days are rejected.
     */
    public static final String PARTIAL_APPROVED_DAY_IDS   = "partialApprovedDayIds";

    /**
     * Process variable stored on the Flowable execution so that downstream
     * service tasks (hrReview, deductLeaveBalance, …) know the original leave
     * was only partially approved by the manager.
     */
    public static final String PARTIAL_APPROVAL            = "partialApproval";

    /** Status value written to the Leave record that was partially approved
     *  and forwarded to HR. */
    public static final String STATUS_PARTIAL_APPROVED     = "PARTIAL_APPROVED";

    // ── Day-type values stored in leave_day_details ────────────────────────────
    public static final String DAY_TYPE_FULL_DAY           = "FULL_DAY";
    public static final String DAY_TYPE_HALF_DAY           = "HALF_DAY";

    // ── Leave-balance deduction ────────────────────────────────────────────────
    /**
     * Deduction weight for a full day.  Stored as a double so arithmetic
     * is straightforward when updating the JSON balance.
     */
    public static final double DEDUCTION_FULL_DAY          = 1.0;

    /**
     * Deduction weight for a half day.
     */
    public static final double DEDUCTION_HALF_DAY          = 0.5;



    // Mail Template Paths
    public static final String TEMPLATE_LEAVE_REQ_MANAGER = "classpath:templates/notifyManagerfromEmployee.html";
    public static final String TEMPLATE_REMINDER_MANAGER = "classpath:templates/reminderMail.html";
    public static final String TEMPLATE_MANAGER_REJECTION = "classpath:templates/managerRejectionMail.html";
    public static final String TEMPLATE_HR_APPROVAL = "classpath:templates/hrApprovalMail.html";
    public static final String TEMPLATE_HR_REJECTION = "classpath:templates/hrRejectionMail.html";
    public static final String TEMPLATE_NOTIFY_HR = "classpath:templates/notifyHRfromManager.html";

    // Template Placeholders
    public static final String PH_USER_ID = "{{userId}}";
    public static final String PH_EMPLOYEE_EMAIL = "{{employeeEmail}}";
    public static final String PH_LEAVE_TYPE = "{{leaveType}}";
    public static final String PH_FROM_DATE = "{{fromDate}}";
    public static final String PH_TO_DATE = "{{toDate}}";
    public static final String PH_REASON = "{{reason}}";
    public static final String PH_ACTION_URL = "{{actionUrl}}";
    public static final String PH_REJECTION_REASON = "{{rejectionReason}}";
    public static final String PH_EMPLOYEE_NAME = "{{employeeName}}";
    public static final String PH_USER_FULL_NAME = "{{fullName}}";
}
