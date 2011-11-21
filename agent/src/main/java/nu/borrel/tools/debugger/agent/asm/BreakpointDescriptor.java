package nu.borrel.tools.debugger.agent.asm;

import java.util.Iterator;
import java.util.NoSuchElementException;

import nu.borrel.tools.debugger.agent.asm.asm4.Label;

public class BreakpointDescriptor {
	public static class LocalVariable implements Cloneable {
		private final String name;
		private final String desc;
		private final Label start;
		private final Label end;
		private final int index;
		private LocalVariable next;
		
		public LocalVariable(String name, String desc, Label start, Label end, int index, LocalVariable next) {
			this.name = name;
			this.desc = desc;
			this.start = start;
			this.end = end;
			this.index = index;
			this.next = next;
		}

		public String getName() {
			return name;
		}

		public String getDesc() {
			return desc;
		}

		public Label getStart() {
			return start;
		}

		public Label getEnd() {
			return end;
		}

		public int getIndex() {
			return index;
		}

		public LocalVariable getNext() {
			return next;
		}
		
		@Override
		public LocalVariable clone() {
			try {
				return (LocalVariable) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new RuntimeException("Impossible", e);
			}
		}
	}
	
	private final String className;
	private final String methodName;
	private final int line;
	
	private LocalVariable lvHead;
	private Label label;
		
	public BreakpointDescriptor(String className, String methodName, Integer line) {
		this.className = className;
		this.methodName = methodName;
		this.line = line;
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

	public void setLabel(Label label) {
		this.label = label;
	}
	
	public Label getLabel() {
		return label;
	}
	
	public boolean isValid() {
		return label != null;
	}

	public void addLocalVariable(String name, String desc, Label start,	Label end, int index) {
		lvHead = new LocalVariable(name, desc, start, end, index, lvHead);
	}
	
	public Iterable<LocalVariable> getLocalVariables() {
		return new Iterable<LocalVariable>() {
			@Override
			public Iterator<LocalVariable> iterator() {
				return new Iterator<LocalVariable>() {
					private LocalVariable lv = lvHead;
					private LocalVariable next = null;

					@Override
					public boolean hasNext() {
						while (next == null && lv != null) {
							if ((lv.start == null || lv.start.getLine() < label.getLine()) && (lv.end == null || lv.end.getLine() >= label.getLine())) {
								next = lv.clone();
								next.next = null;
							}
							lv = lv.next;
						}
						return next != null;
					}

					@Override
					public LocalVariable next() {
						if (hasNext()) {
							LocalVariable result = next;
							next = null;
							return result;
						}
						throw new NoSuchElementException();
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException("Not implemented");
					}
				};
			}
		};
	}
}
