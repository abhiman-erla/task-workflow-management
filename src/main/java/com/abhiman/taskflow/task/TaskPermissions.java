package com.abhiman.taskflow.task;

import com.abhiman.taskflow.auth.AuthenticatedUser;
import com.abhiman.taskflow.user.User;
import org.springframework.stereotype.Component;

/**
 * Ownership-based authorization for tasks. Role-only rules could live in @PreAuthorize,
 * but "is this user the assignee?" needs the loaded task, so the rules live here where
 * they can be unit-tested without Spring.
 */
@Component
public class TaskPermissions {

    public boolean canEdit(Task task, AuthenticatedUser me) {
        return me.isManagerOrAdmin() || isSameUser(task.getCreatedBy(), me) || isSameUser(task.getAssignee(), me);
    }

    public boolean canAssign(AuthenticatedUser me) {
        return me.isManagerOrAdmin();
    }

    public boolean canDelete(AuthenticatedUser me) {
        return me.isManagerOrAdmin();
    }

    public boolean canTransition(Task task, TaskStatus target, AuthenticatedUser me) {
        if (me.isManagerOrAdmin()) {
            return true;
        }
        // Members move only their own tasks, and can't approve their own work.
        boolean approving = task.getStatus() == TaskStatus.IN_REVIEW && target == TaskStatus.DONE;
        return isSameUser(task.getAssignee(), me) && !approving;
    }

    private static boolean isSameUser(User user, AuthenticatedUser me) {
        return user != null && user.getId() != null && user.getId().equals(me.id());
    }
}
