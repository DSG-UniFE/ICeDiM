package core;

public class SeedGeneratorHelper {
	static final byte[] DEFAULT_STRING = hexStringToByteArray("0A0B0C0D0E0F1A1B1C1D1E1F2A2B2C2D");
	
	public static byte[] hexStringToByteArray(String s) {
	    if ((s.length() % 2) != 0) {
	    	s = "0" + s;
	    }
	    
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    
	    return data;
	}
	
	public static byte[] get16BytesSeedFromValue(long seed) {
		byte[] hexString = hexStringToByteArray(Long.toHexString(seed));
		byte[] res = new byte[DEFAULT_STRING.length];
		System.arraycopy(DEFAULT_STRING, 0, res, 0, DEFAULT_STRING.length - hexString.length);
		System.arraycopy(hexString, 0, res, DEFAULT_STRING.length - hexString.length, hexString.length);
		
		return res;
	}
	
}
