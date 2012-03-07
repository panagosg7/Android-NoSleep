package edu.ucsd.salud.mcmutton;

public class FailedManifestException extends ApkException {
	private static final long serialVersionUID = 7652707394391883564L;

	public FailedManifestException(String err) {
		super(err);
	}
}
