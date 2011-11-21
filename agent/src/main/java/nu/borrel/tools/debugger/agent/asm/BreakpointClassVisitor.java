package nu.borrel.tools.debugger.agent.asm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Iterator;

import nu.borrel.tools.debugger.agent.asm.BreakpointDescriptor.LocalVariable;
import nu.borrel.tools.debugger.agent.asm.asm4.ClassVisitor;
import nu.borrel.tools.debugger.agent.asm.asm4.Label;
import nu.borrel.tools.debugger.agent.asm.asm4.MethodVisitor;
import nu.borrel.tools.debugger.agent.asm.asm4.Opcodes;
import nu.borrel.tools.debugger.agent.asm.asm4.Type;
import nu.borrel.tools.debugger.agent.asm.asm4.commons.LocalVariablesSorter;
import nu.borrel.tools.debugger.agent.asm.asm4.util.TraceClassVisitor;

class BreakpointClassVisitor extends ClassVisitor implements Opcodes {
	private final BreakpointDescriptor descriptor;
	
	public BreakpointClassVisitor(ClassVisitor cv, BreakpointDescriptor descriptor) throws FileNotFoundException {
		super(ASM4, new TraceClassVisitor(cv, new PrintWriter(new File("/tmp/output.txt"))));
		this.descriptor = descriptor;
	}

	@Override
	public MethodVisitor visitMethod(final int access, final String name, String desc, String signature, String[] exceptions) {
		MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
		if (name.equals(descriptor.getMethodName())) {
			mv = new LocalVariablesSorter(access, desc, mv) {
				@Override
				public void visitLabel(Label label) {
					// Ideally we insert before the existing label, so that debug information stays stable.
					// But: this could mean we get jumped over!
					super.visitLabel(label);
					if (label.getLine() == descriptor.getLabel().getLine()) {
						inject();
					}
				}
				
				private void inject() {
					// Dump all local variables into a Map, and provide that map to the agent call as well
					Iterator<LocalVariable> it = descriptor.getLocalVariables().iterator();
					int localIndex = -1;					
					mv.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V");
					localIndex = newLocal(Type.getObjectType("java/util/HashMap"));
					mv.visitVarInsn(ASTORE, localIndex);
					if (it.hasNext()) {
						for (LocalVariable lv : descriptor.getLocalVariables()) {
							mv.visitVarInsn(ALOAD, localIndex);
							mv.visitLdcInsn(lv.getName());
							loadLocalVariableValueAsObject(lv);
							mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
							mv.visitInsn(POP);
						}
					}
					visitFieldInsn(GETSTATIC, "nu/borrel/tools/debugger/agent/DebuggerAgent", "instance", "Lnu/borrel/tools/debugger/agent/DebuggerAgent;");
					visitInsn(POP);
					if ((access & ACC_STATIC) != 0) {
						visitLdcInsn(Type.getType("L" + descriptor.getClassName() + ";"));
					} else {
						visitVarInsn(ALOAD, 0);
					}
					if (localIndex >= 0) {
						visitVarInsn(ALOAD, localIndex);
					} else {
						visitInsn(ACONST_NULL);
					}
					visitLdcInsn(name);
					// XXX: SIPUSH/BIPUSH or LDC new Integer() depending on size.
					visitIntInsn(SIPUSH, descriptor.getLine());
					visitMethodInsn(INVOKESTATIC, "nu/borrel/tools/debugger/agent/DebuggerAgent", "breakpointHit", "(Ljava/lang/Object;Ljava/util/Map;Ljava/lang/String;I)V");
				}
				
				private void loadLocalVariableValueAsObject(LocalVariable lv) {
					// The lv.getIndex() refers to *original* offsets, and so needs remapping
					// Depending on lv.getDesc() we need to use a different approach here to load it:
					// I/D/L/F etc need to go via their own valueOf method.
					Type type = Type.getType(lv.getDesc());
					switch (type.getSort()) {
					case Type.BOOLEAN:
						visitVarInsn(ILOAD, lv.getIndex());
						mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
						break;
					case Type.CHAR:
						visitVarInsn(ILOAD, lv.getIndex());
						mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;");
						break;
					case Type.BYTE:
						visitVarInsn(ILOAD, lv.getIndex());
						mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(B)Ljava/lang/Byte;");
						break;
					case Type.SHORT:
						visitVarInsn(ILOAD, lv.getIndex());
						mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;");
						break;
					case Type.INT:
						visitVarInsn(ILOAD, lv.getIndex());
						mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;");
						break;
					case Type.FLOAT:
						visitVarInsn(FLOAD, lv.getIndex());
						mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;");
						break;
					case Type.LONG:
						visitVarInsn(LLOAD, lv.getIndex());
						mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(L)Ljava/lang/Long;");
						break;
					case Type.DOUBLE:
						visitVarInsn(DLOAD, lv.getIndex());
						mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;");
						break;
					case Type.ARRAY:
						visitVarInsn(AALOAD, lv.getIndex());
						break;
					case Type.OBJECT:
						visitVarInsn(ALOAD, lv.getIndex());
						break;
					}					
				}
			};
		}
		return mv;
	}
}