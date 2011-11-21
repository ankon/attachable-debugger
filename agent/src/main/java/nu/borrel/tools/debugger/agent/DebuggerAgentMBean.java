package nu.borrel.tools.debugger.agent;

public interface DebuggerAgentMBean {
	int setBreakpoint(String className, String methodName, int pc);

	String getVersion();
}
