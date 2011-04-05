package storage;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class MboRecordDataImpl implements InvocationHandler {

	private Object existingMaximoData;

	public MboRecordDataImpl(Object existingMaximoData) {
		this.existingMaximoData=existingMaximoData;
	}

	public Object invoke(Object arg0, Method arg1, Object[] arg2)
			throws Throwable {
		return arg1.invoke(arg0, arg2);
		
	}

}
