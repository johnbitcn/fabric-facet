package com.facet.client;

public final class MouseScrollPolicy {
	private MouseScrollPolicy() {
	}

	public static boolean shouldBlock(boolean disabled, boolean inWorld, boolean screenOpen) {
		return disabled && inWorld && !screenOpen;
	}
}
