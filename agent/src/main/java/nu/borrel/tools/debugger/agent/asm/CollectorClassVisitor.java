package nu.borrel.tools.debugger.agent.asm;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import nu.borrel.tools.debugger.agent.asm.asm4.ClassVisitor;
import nu.borrel.tools.debugger.agent.asm.asm4.Label;
import nu.borrel.tools.debugger.agent.asm.asm4.MethodVisitor;
import nu.borrel.tools.debugger.agent.asm.asm4.Opcodes;
import nu.borrel.tools.debugger.agent.asm.asm4.Type;

public class CollectorClassVisitor extends ClassVisitor implements Opcodes {
	private final String className;
	// XXX: unfold into a chain!
	private final Map<String, Integer> breakpoints;
	private final List<BreakpointDescriptor> descriptors = new LinkedList<BreakpointDescriptor>();

	CollectorClassVisitor(Map<String, Integer> breakpoints, String className) {
		super(ASM4);
		this.className = className;
		this.breakpoints = breakpoints;
	}

	@Override
	public MethodVisitor visitMethod(final int access, final String name, String desc, String signature, String[] exceptions) {
		final Integer expectedLine = breakpoints.get(name); 
		if (expectedLine != null) {
			final BreakpointDescriptor descriptor = new BreakpointDescriptor(className, name, expectedLine.intValue());
			int i = 0;
			for (Type t : Type.getArgumentTypes(desc)) {
				descriptor.addLocalVariable("arg" + i, t.getDescriptor(), null, null, i + ((access & ACC_STATIC) == 0 ? 1 : 0));
				i++;
			}
			
			return new MethodVisitor(ASM4) {
				@Override
				public void visitLineNumber(int line, Label start) {
					if (line >= expectedLine.intValue() && !descriptor.isValid()) {
						descriptor.setLabel(start);
					}
				}

				@Override
				public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
					descriptor.addLocalVariable(name, desc, start, end, index);
				}

				@Override
				public void visitEnd() {
					if (descriptor.isValid()) {
						descriptors.add(descriptor);
					}
				}
			};
		}
		return null;
	}
	
	public Iterable<BreakpointDescriptor> getBreakpointDescriptors() {
		return descriptors;
	}
}
