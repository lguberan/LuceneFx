package com.guberan.lucenefx;

/**
 * Launcher
 */
public class Launcher {

	/**
	 * Call real LuceneFx main() method, required because of (a BUG?) of JavaFx 13
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		LuceneFx.main(args);
	}

}
