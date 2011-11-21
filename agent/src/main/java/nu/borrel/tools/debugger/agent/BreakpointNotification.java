package nu.borrel.tools.debugger.agent;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.Notification;

public class BreakpointNotification extends Notification {
	private static final long serialVersionUID = DebuggerAgent.VERSION;
	private static final AtomicLong sequenceNumber = new AtomicLong();
	private final String className;
	private final String methodName;
	private final int line;
	private final StackTraceElement[] stack;
	private final Map<String, Serializable> localVariables = new HashMap<String, Serializable>();

	public BreakpointNotification(Object source, Object who, String name, int line, StackTraceElement[] stack) {
		super(BreakpointNotification.class.getName(), source, sequenceNumber.incrementAndGet(), System.currentTimeMillis());
		this.className = who.getClass().getName();
		this.methodName = name;
		this.line = line;
		this.stack = stack;
	}

	public String getClassName() {
		return className;
	}

	public String getMethodName() {
		return methodName;
	}

	public int getLine() {
		return line;
	}

	public StackTraceElement[] getStackTrace() {
		return stack;
	}
	
	public Map<String, Serializable> getLocalVariables() {
		return localVariables;
	}
}