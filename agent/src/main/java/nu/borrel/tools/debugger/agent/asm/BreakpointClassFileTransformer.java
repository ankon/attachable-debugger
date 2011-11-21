package nu.borrel.tools.debugger.agent.asm;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import nu.borrel.tools.debugger.agent.asm.asm4.ClassReader;
import nu.borrel.tools.debugger.agent.asm.asm4.ClassVisitor;
import nu.borrel.tools.debugger.agent.asm.asm4.ClassWriter;

public class BreakpointClassFileTransformer implements ClassFileTransformer {
	private final String className;
	private final Map<String, Integer> breakpoints = new HashMap<String, Integer>();
	
	public BreakpointClassFileTransformer(String className) {
		this.className = className.replaceAll("\\.", "/");
	}
	
	@Override
	public byte[] transform(ClassLoader loader, final String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		if (this.className.equals(className)) {
			System.out.println("[" + className + "]: Transforming");
			try {
				// Phase 1: collect information about labels and local variables
				ClassReader cr = new ClassReader(classfileBuffer);
				CollectorClassVisitor collector = new CollectorClassVisitor(breakpoints, className);
				cr.accept(collector, 0);
				
				ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);
				// Unfold all matched breakpoint information into separate visitors, and chain them.
				ClassVisitor cv = cw;
				for (BreakpointDescriptor bd : collector.getBreakpointDescriptors()) {
					cv = new BreakpointClassVisitor(cv, bd);
				}
				// Phase 2: write the new class
				cr = new ClassReader(classfileBuffer);
				cr.accept(cv, ClassReader.EXPAND_FRAMES);
				
				byte[] bytes = cw.toByteArray();
				
				// DEBUG
				File tmpFile = File.createTempFile("debug", ".class");
				System.out.println("Dumping into " + tmpFile.getAbsolutePath());
				FileOutputStream fos = new FileOutputStream(tmpFile);
				try {
					fos.write(bytes);
				} finally {
					fos.close();
				}
				// END-DEBUG
				
				return bytes;
			} catch (Throwable t) {
				System.err.println("Caught throwable");
				t.printStackTrace();
			}
		}
		return null;
	}
	
	public void addBreakpoint(String methodName, int pc) {
		breakpoints.put(methodName, pc);
	}
}