package nu.borrel.tools.debugger.client;

import java.io.Console;
import java.io.File;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import nu.borrel.tools.debugger.agent.BreakpointNotification;
import nu.borrel.tools.debugger.agent.DebuggerAgent;
import nu.borrel.tools.debugger.agent.DebuggerAgentMBean;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

public class DebuggerClient {

	private static final String JMX_LOCAL_CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";

	public interface Command {
		String call(DebuggerAgentMBean agent, JMXConnector connector) throws Exception;
	}

	public static class DumpThreadsCommand implements Command {
		@Override
		public String call(DebuggerAgentMBean agent, JMXConnector connector) throws Exception {
			StringBuilder result = new StringBuilder();
			
			MBeanServerConnection mbsc = connector.getMBeanServerConnection();
			ObjectName objName = new ObjectName(ManagementFactory.THREAD_MXBEAN_NAME);
			Set<ObjectName> mbeans = mbsc.queryNames(objName, null);
			for (ObjectName name : mbeans) {
				ThreadMXBean threadBean;
				threadBean = ManagementFactory.newPlatformMXBeanProxy(mbsc, name.toString(), ThreadMXBean.class);
				long threadIds[] = threadBean.getAllThreadIds();
				for (long threadId : threadIds) {
					ThreadInfo threadInfo = threadBean.getThreadInfo(threadId);
					System.out.println(threadInfo.getThreadName() + " / " + threadInfo.getThreadState());
				}
			}
			
			return result.toString();
		}
	}
	
	public static class NopCommand implements Command {
		@Override
		public String call(DebuggerAgentMBean agent, JMXConnector connector) throws Exception {
			return "";
		}
	}
	
	public static class ErrorCommand implements Command {
		private final String error;
		
		public ErrorCommand(String error) {
			this.error = error;
		}
		
		@Override
		public String call(DebuggerAgentMBean agent, JMXConnector connector) throws Exception {
			return "ERROR: " + error;
		}
	}
	
	public static class QuitCommand implements Command {
		@Override
		public String call(DebuggerAgentMBean agent, JMXConnector connector) throws Exception {
			System.exit(0);
			return "NOT REACHED";
		}
	}
	
	public static class BreakpointCommand implements Command {
		private final String className;
		private final String methodName;
		private final int pc;
		
		public BreakpointCommand(String className, String methodName, int pc) {
			this.className = className;
			this.methodName = methodName;
			this.pc = pc;
		}

		@Override
		public String call(DebuggerAgentMBean agent, JMXConnector connector) throws Exception {
			int result = agent.setBreakpoint(className, methodName, pc);
			return "Created breakpoint " + result;
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			System.err.println("Please provide process id or main class name");			
			for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
				System.out.println(vmd.id() + " " + vmd.displayName());
			}
			System.exit(-1);
		}
		
		// sun-specific, need to find a nicer way.
		String vmid = args[0];
		try {
			Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			for (VirtualMachineDescriptor vmd : VirtualMachine.list()) {
				if (args[0].equals(vmd.displayName().split(" ", 2)[0])) {
					vmid = vmd.id();
				}
			}
		}
		
		Console console = System.console();
		if (console == null) {
			System.err.println("Cannot get a console");
			System.exit(-1);
		}

		File agentJarFile = new File("agent.jar");
		if (!agentJarFile.isFile()) {
			System.err.println("Cannot find agent " + agentJarFile);
			System.exit(-1);
		}
		String agentPath = agentJarFile.getAbsolutePath();
		
		VirtualMachine vm = VirtualMachine.attach(vmid);
		String connectorAddr = vm.getAgentProperties().getProperty(JMX_LOCAL_CONNECTOR_ADDRESS);
		if (connectorAddr == null) {
			String agent = vm.getSystemProperties().getProperty("java.home") + File.separator + "lib" + File.separator + "management-agent.jar";
			vm.loadAgent(agent);
			connectorAddr = vm.getAgentProperties().getProperty(JMX_LOCAL_CONNECTOR_ADDRESS);
		}
		JMXServiceURL serviceURL = new JMXServiceURL(connectorAddr);
		JMXConnector connector = JMXConnectorFactory.connect(serviceURL);
		try {
			// Try to find the agent, if not: load it as well.
			if (!connector.getMBeanServerConnection().isRegistered(DebuggerAgent.createObjectName())) {
				vm.loadAgent(agentPath);
			}
			
			ObjectName agentName = DebuggerAgent.createObjectName();
			DebuggerAgentMBean agent = JMX.newMBeanProxy(connector.getMBeanServerConnection(), agentName, DebuggerAgentMBean.class, true);
			System.out.println("Connected to " + vm.id() + ", agent version " + agent.getVersion());
			// NB: no filter, as that would require the ability to send the implementation of the filter to the debuggee.
			//     That may not be possible depending on security settings.
			connector.getMBeanServerConnection().addNotificationListener(agentName, new NotificationListener() {
				@Override
				public void handleNotification(Notification n, Object handback) {
					if (n instanceof BreakpointNotification) {
						BreakpointNotification notification = (BreakpointNotification) n;
						System.out.println("Breakpoint: " + notification.getClassName() + "#" + notification.getMethodName());
						for (StackTraceElement ste : notification.getStackTrace()) {
							System.out.println("\tat " + ste.getClassName() + "." + ste.getMethodName() + "(" + ste.getFileName() + ":" + ste.getLineNumber() + ")");
						}
						for (Map.Entry<String, Serializable> local : notification.getLocalVariables().entrySet()) {
							System.out.println("\t   " + local.getKey() + " = " + local.getValue());
						}
					}
				}
			}, null, null);
			
			while (true) {
				Command command = new NopCommand();
				console.printf("> ");
				String commandString = console.readLine();
				String[] words = commandString.split(" +");
				if ("threads".equals(words[0])) {
					command = new DumpThreadsCommand();
				} else if (Arrays.asList("quit", "exit", "bye").contains(words[0])) {
					command = new QuitCommand();
				} else if ("bp".equals(words[0]) && words.length == 4) {
					command = new BreakpointCommand(words[1], words[2], Integer.parseInt(words[3]));
				} else {
					command = new ErrorCommand("Syntax Error: '" + commandString + "'");
				}
				
				String result = command.call(agent, connector);
				System.out.println(result);
			}
		} finally {
			connector.close();
		}
	}

}
