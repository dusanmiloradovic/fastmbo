package storage;

public class MboRecordDataF {

	public static MboRecordDataI getMboRecordData(Object existingMaximoData){
		return (MboRecordDataI) java.lang.reflect.Proxy.newProxyInstance(
				existingMaximoData.getClass().getClassLoader(),
				new Class[] { MboRecordDataI.class }, new MboRecordDataImpl(
						existingMaximoData));

	}
}
