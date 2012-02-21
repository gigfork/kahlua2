/*
 Copyright (c) 2012 Kristofer Karlsson <kristofer.karlsson@gmail.com>

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.
 */

package com.headius.kahlua;

import org.luaj.kahluafork.compiler.LexState;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.TraceClassVisitor;
import se.krka.kahlua.vm.*;
import static se.krka.kahlua.vm.KahluaThread.*;

import java.io.*;

public class LuaCompiler implements JavaFunction {

	private final int index;

	private static final int LOADSTRING = 0;
	private static final int LOADSTREAM = 1;
	private static final String[] names = new String[] {
		"loadstring",
		"loadstream",
	};

	private static final LuaCompiler[] functions = new LuaCompiler[names.length];
	static {
		for (int i = 0; i < names.length; i++) {
			functions[i] = new LuaCompiler(i);
		}
	}

	private LuaCompiler(int index) {
		this.index = index;
	}

	public static void register(KahluaTable env) {
		for (int i = 0; i < names.length; i++) {
			env.rawset(names[i], functions[i]);
		}
		/*
		KahluaTable packageTable = (KahluaTable) env.rawget("package");
		KahluaTable loadersTable = (KahluaTable) packageTable.rawget("loaders");
		*/
	}

	public int call(LuaCallFrame callFrame, int nArguments) {
		switch (index) {
		case LOADSTRING: return loadstring(callFrame, nArguments);
		case LOADSTREAM: return loadstream(callFrame, nArguments);
		}
		return 0;
	}

	public static int loadstream(LuaCallFrame callFrame, int nArguments) {
		try {
			KahluaUtil.luaAssert(nArguments >= 2, "not enough arguments");
			Object input = callFrame.get(0);
			KahluaUtil.luaAssert(input != null, "No input given");
			String name = (String) callFrame.get(1);
			if (input instanceof Reader) {
				return callFrame.push(loadis((Reader) input, name, null, callFrame.getEnvironment()));
			}
			if (input instanceof InputStream) {
				return callFrame.push(loadis((InputStream) input, name, null, callFrame.getEnvironment()));
			}
			KahluaUtil.fail("Invalid type to loadstream: " + input.getClass());
			return 0;
		} catch (RuntimeException e) {
			return callFrame.push(null, e.getMessage());
		} catch (IOException e) {
			return callFrame.push(null, e.getMessage());
		}
	}

	private int loadstring(LuaCallFrame callFrame, int nArguments) {
		try {
			KahluaUtil.luaAssert(nArguments >= 1, "not enough arguments");
			String source = (String) callFrame.get(0);
			KahluaUtil.luaAssert(source != null, "No source given");
			String name = null;
            if (nArguments >= 2) {
                name = (String) callFrame.get(1);
            }
			return callFrame.push(loadstring(source, name, callFrame.getEnvironment()));
		} catch (RuntimeException e) {
			return callFrame.push(null, e.getMessage());
		} catch (IOException e) {
			return callFrame.push(null, e.getMessage());
		}
	}

    public static LuaClosure loadis(InputStream inputStream, String name, KahluaTable environment) throws IOException {
        return loadis(inputStream, name, null, environment);
    }

    public static LuaClosure loadis(Reader reader, String name, KahluaTable environment) throws IOException {
        return loadis(reader, name, null, environment);
    }

	public static LuaClosure loadstring(String source, String name, KahluaTable environment) throws IOException {
        return loadis(new ByteArrayInputStream(source.getBytes("UTF-8")), name, source, environment);
    }

	private static LuaClosure loadis(Reader reader, String name, String source, KahluaTable environment) throws IOException {
		Prototype p = LexState.compile(reader.read(), reader, name, source);
		byte[] cls = compile(p, environment);

		TraceClassVisitor tcv = new TraceClassVisitor(new PrintWriter(System.out));
		new ClassReader(cls).accept(tcv, 0);
		return new LuaClosure(p, environment);
	}
	
	private static byte[] compile(Prototype p, KahluaTable environment) {
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
		cw.visit(Opcodes.V1_7, Opcodes.ACC_PUBLIC, "Blah", null, "java/lang/Object", null);
		Method m = Method.getMethod("void blah()");
		GeneratorAdapter ga = new GeneratorAdapter(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, m, null, null, cw);

		int[] opcodes = p.code;

		int a, b, c;

		for (int i = 0; i < p.maxStacksize; i++) {
			System.out.println("declared local: " + ga.newLocal(Type.getType(Object.class)));
		}

		for (int op : opcodes) {
			int opcode = op & 63;

			switch (opcode) {
				case OP_MOVE:
					a = getA8(op);
					b = getB9(op);
					System.out.println("move from " + b + " to " + a);
					ga.loadLocal(b);
					ga.storeLocal(a);
					break;
				case OP_LOADK: {
					a = getA8(op);
					b = getBx(op);
					Object constant = p.constants[b];
					System.out.println("store constant " + constant + " to " + a);
					if (constant instanceof Double) {
						ga.push((Double)constant);
						ga.invokeVirtual(Type.getType(Double.class), Method.getMethod("java.lang.Double valueOf(double)"));
						ga.storeLocal(a);
					}
					break;
				}
				default:
					System.out.println("unhandled: " + opcode);
			}
		}

		ga.endMethod();
		cw.visitEnd();

		return cw.toByteArray();
	}

	private static LuaClosure loadis(InputStream inputStream, String name, String source, KahluaTable environment) throws IOException {
		return loadis(new InputStreamReader(inputStream), name, source, environment);
	}
}

