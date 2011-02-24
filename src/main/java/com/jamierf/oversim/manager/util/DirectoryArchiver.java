package com.jamierf.oversim.manager.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

public class DirectoryArchiver {

	public static enum ArchiveType { TAR_GZIP, ZIP };

	protected final ArchiveType type;

	public DirectoryArchiver() {
		this (ArchiveType.TAR_GZIP);
	}

	public DirectoryArchiver(ArchiveType type) {
		this.type = type;
	}

	public void compress(File directory, File archive) throws IOException {
		ArchiveOutputStream out = null;

		try {
			out = createArchiveOutputStream(new BufferedOutputStream(new FileOutputStream(archive)));
			addFiletoArchive(out, directory, "");
		}
		finally {
			if (out != null) {
				out.finish();
				out.close();
			}
		}
	}

	private ArchiveOutputStream createArchiveOutputStream(OutputStream out) throws IOException {
		switch (type) {
		case TAR_GZIP:
			return new TarArchiveOutputStream(new GzipCompressorOutputStream(out));
		case ZIP:
			return new ZipArchiveOutputStream(out);
		default:
			return null;
		}
	}

	private ArchiveEntry createArchiveEntry(File file, String name) throws IOException {
		switch (type) {
		case TAR_GZIP:
			return new TarArchiveEntry(file, name);
		case ZIP:
			return new ZipArchiveEntry(file, name);
		default:
			return null;
		}
	}

	private void addFiletoArchive(ArchiveOutputStream out, File file, String base) throws IOException {
		String name = base + file.getName();

		// Add the entry to the archive
		out.putArchiveEntry(createArchiveEntry(file, name));

		if (file.isDirectory()) {
			out.closeArchiveEntry();

			File[] children = file.listFiles();
			for (File child : children)
				addFiletoArchive(out, child, name + "/");
		}
		else {
			InputStream in = null;

			try {
				in = new FileInputStream(file);
				IOUtils.copy(in, out);
			}
			finally {
				if (in != null)
					in.close();
			}

			out.closeArchiveEntry();
		}
	}
}
