package com.abhiman.taskflow.task;

import com.abhiman.taskflow.common.ConflictException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.abhiman.taskflow.task.TaskStatus.CANCELLED;
import static com.abhiman.taskflow.task.TaskStatus.DONE;
import static com.abhiman.taskflow.task.TaskStatus.IN_PROGRESS;
import static com.abhiman.taskflow.task.TaskStatus.IN_REVIEW;
import static com.abhiman.taskflow.task.TaskStatus.TODO;

/**
 * The task lifecycle as an explicit state machine.
 *
 * <pre>
 * TODO ──► IN_PROGRESS ──► IN_REVIEW ──► DONE
 *   ▲          │  ▲             │
 *   └──────────┘  └─────────────┘   (send back)
 * TODO / IN_PROGRESS / IN_REVIEW ──► CANCELLED
 * </pre>
 *
 * Keeping the rules in one table (instead of if/else spread across services) makes them easy
 * to read, test exhaustively, and change.
 */
@Component
public class TaskWorkflow {

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS = new EnumMap<>(TaskStatus.class);

    static {
        TRANSITIONS.put(TODO, EnumSet.of(IN_PROGRESS, CANCELLED));
        TRANSITIONS.put(IN_PROGRESS, EnumSet.of(IN_REVIEW, TODO, CANCELLED));
        TRANSITIONS.put(IN_REVIEW, EnumSet.of(DONE, IN_PROGRESS, CANCELLED));
        TRANSITIONS.put(DONE, EnumSet.noneOf(TaskStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(TaskStatus.class));
    }

    public Set<TaskStatus> allowedNext(TaskStatus from) {
        return EnumSet.copyOf(TRANSITIONS.get(from)); // defensive copy: callers can't mutate the rules
    }

    public boolean canTransition(TaskStatus from, TaskStatus to) {
        return TRANSITIONS.get(from).contains(to);
    }

    /**
     * Validates and performs the transition.
     *
     * @return the status the task had before the change (for the audit history)
     * @throws ConflictException if the transition is not allowed in the current state
     */
    public TaskStatus apply(Task task, TaskStatus target) {
        TaskStatus current = task.getStatus();
        if (!canTransition(current, target)) {
            throw new ConflictException("Cannot move task from " + current + " to " + target
                    + ". Allowed next states: " + allowedNext(current));
        }
        if (target == IN_PROGRESS && task.getAssignee() == null) {
            throw new ConflictException("Task needs an assignee before it can move to IN_PROGRESS");
        }
        task.setStatus(target);
        return current;
    }
}
