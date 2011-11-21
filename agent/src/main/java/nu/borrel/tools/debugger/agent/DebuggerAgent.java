package nu.borrel.tools.debugger.agent;

import java.io.Serializable;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.ObjectName;

import nu.borrel.tools.debugger.agent.asm.BreakpointClassFileTransformer;


public class DebuggerAgent extends NotificationBroadcasterSupport implements DebuggerAgentMBean, NotificationEmitter {
	public static long VERSION = 1;
	public static DebuggerAgent instance;
	
	private final Instrumentation instrumentation;
	private final Map<String, BreakpointClassFileTransformer> breakpoints = new HashMap<String, BreakpointClassFileTransformer>();
	
	public DebuggerAgent(Instrumentation instrumentation) {
		this.instrumentation = instrumentation;
	}

	public static void agentmain(String agentArgs, Instrumentation instrumentation) throws InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException, MalformedObjectNameException, NullPointerException {
		// Nothing to do except to register the JMX bean that handles the communication.
		MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		
		instance = new DebuggerAgent(instrumentation);
		server.registerMBean(instance, createObjectName());
	}

	@Override
	public int setBreakpoint(String className, String methodName, int pc) {
		// Retransform the class to apply all breakpoints we know about.
		// For the time being: breakpoints just dump the locals into the notification listener.
		BreakpointClassFileTransformer transformer = breakpoints.get(className);
		if (transformer == null) {
			breakpoints.put(className, transformer = new BreakpointClassFileTransformer(className));
			instrumentation.addTransformer(transformer, true);
		}
		transformer.addBreakpoint(methodName, pc);
		
		for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
			if (className.equals(loadedClass.getName())) {
				System.out.println("Retransforming " + loadedClass);
				try {
					instrumentation.retransformClasses(loadedClass);
				} catch (UnmodifiableClassException e) {
					System.out.println("Cannot set breakpoint in " + loadedClass + ": " + e);
				}
			}
		}
		return /* FIXME */ 0;
	}
	
	public static ObjectName createObjectName() {
		try {
			return new ObjectName(DebuggerAgent.class.getPackage().getName(), "type", "Agent");
		} catch (Exception e) {
			throw new RuntimeException("Cannot create ObjectName", e);
		}		
	}

	@Override
	public String getVersion() {
		return "" + VERSION;
	}
	
	// entrypoint, modify the ASM stuff!!!!!!!
	public static void breakpointHit(Object who, Map<String, Object> locals, String name, int line) {
		instance.notifyBreakpointHit(who, locals, name, line);
	}
	
	private void notifyBreakpointHit(Object who, Map<String, Object> locals, String name, int line) {
		// XXX: breakpoints in Class are therefore always static :)
		StackTraceElement[] place = Thread.currentThread().getStackTrace();
		BreakpointNotification notification = new BreakpointNotification(this, who, name, line, Arrays.copyOfRange(place, 2, place.length));
		if (locals != null) {
			for (Map.Entry<String, Object> me : locals.entrySet()) {
				Serializable v;
				if (me.getValue() == null || me.getValue() instanceof Serializable) {
					v = (Serializable) me.getValue();
				} else {
					v = me.getValue().toString();
				}
				
				notification.getLocalVariables().put(me.getKey(), v);
			}
		}
		sendNotification(notification);
	}
	
	@Override 
	public MBeanNotificationInfo[] getNotificationInfo() {
		String name = BreakpointNotification.class.getName();
		String description = "Breakpoint Hit";
		MBeanNotificationInfo info = new MBeanNotificationInfo(new String[] { name }, name, description);
		return new MBeanNotificationInfo[] { info };
	}
}
