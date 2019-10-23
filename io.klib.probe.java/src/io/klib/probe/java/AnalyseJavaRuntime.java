package io.klib.probe.java;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class AnalyseJavaRuntime {

	private static final String NO_VERSION = "0.0.0";
	private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");
	private static final String JRT_PROTOCOL = "jrt:/";
	private static final String JRT_MODULES = "jrt:/modules";

	public static void main(String[] args) throws IOException {
		long startTimeMillis = System.currentTimeMillis();
		System.out.format("launching app at %s\n", SIMPLE_DATE_FORMAT.format(startTimeMillis));
		String wrkDirString = System.getProperty("user.dir", Files.createTempDirectory("_jre_extraction_").toString())
				.concat("/wrk");
		Path bndFile = Paths.get(wrkDirString, "/import_j2see.bnd");

		File wrkDir = new File(wrkDirString);
		if (!wrkDir.exists())
			wrkDir.mkdirs();

		List<Package> listAllAvailableJavaPackages = listAllJavaPackages();

		Map<Module, List<String>> mapModuleExportedPackages = retrieveMapModuleExportedPackage();
		print2sysout(mapModuleExportedPackages);

		List<String> listOfExportedPackages = mapModuleExportedPackages.values().stream().flatMap(List::stream)
				.distinct().sorted().collect(Collectors.toList());
		String exportcontents = "j2se.exportcontents: \\\n" + formatPackageAsBndExport(listOfExportedPackages) + "\n";
		Files.writeString(bndFile, exportcontents, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.WRITE);

		Map<Module, List<String>> mapModuleNonExportedPackages = retrieveMapModuleNonExportedPackage();

		List<String> listOfNonExportedPackages = mapModuleNonExportedPackages.values().stream().flatMap(List::stream)
				.distinct().sorted().collect(Collectors.toList());
		String includeResourceIgnore = "\nj2se.ignorePackages:!"
				+ formatPackageAsBndIgnoreList(listOfNonExportedPackages) + "\n";
		Files.writeString(bndFile, includeResourceIgnore, StandardCharsets.UTF_8, StandardOpenOption.APPEND);

		extractJavaRuntimeJar(wrkDir);

		System.out.format("execution finished - took %s ms", System.currentTimeMillis() - startTimeMillis);
	}

	private static void print2sysout(Map<Module, List<String>> mapModuleExportedPackages) {
		System.out.format("# <modulename>");
		System.out.println("  <packagename> (exported) ");
		mapModuleExportedPackages.forEach((m, pList) -> {
			System.out.format("# %s\n", m.getName());
			pList.stream().sorted().forEach(p -> System.out.format("    %s\n", p.toString()));
		});
	}

	private static void extractJavaRuntimeJar(File targetDir) {
		Path wrkDirPath = targetDir.toPath().resolve("modules");
		// if there is already an "java.*" folder skip extraction
		File wrkDir = wrkDirPath.toFile();
		if (!wrkDir.exists() || wrkDir.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith("java.");
			}
		}).length == 0) {
			try {
				Path p = Paths.get(URI.create(JRT_MODULES));
				Files.walk(p).filter(Files::isRegularFile).forEach(f -> {
					String srcPathString = f.toAbsolutePath().toString().replaceFirst("/modules/", "");
					Path targetPath = wrkDirPath.resolve(srcPathString);
					URI srcURI;
					try {
						srcURI = new URI(JRT_PROTOCOL + srcPathString);
						System.out.format("extracting %s %s\n", srcPathString, targetPath);
						File parentDir = targetPath.toFile().getParentFile();
						if (!parentDir.exists()) {
							parentDir.mkdirs();
						}
						if (!targetPath.toFile().exists()) {
							Files.copy(srcURI.toURL().openStream(), targetPath);
						}
					} catch (URISyntaxException | IOException e) {
						e.printStackTrace();
					}
				});
			} catch (FileSystemNotFoundException | IOException ex) {
				System.out.println("Could not read my modules (perhaps not Java >8).");
			}
		}
	}

	private static Map<Module, List<String>> retrieveMapModuleExportedPackage() {

		List<Module> listOfNamedModules = ModuleLayer.boot().modules().stream().filter(new Predicate<Module>() {
			// remove probe module
			@Override
			public boolean test(Module m) {
				return m.equals(this.getClass().getModule()) ? false : true;
			}
		}).sorted(new Comparator<Module>() {
			@Override
			public int compare(Module m0, Module m1) {
				return m0.getName().compareTo(m1.getName());
			}
		}).collect(Collectors.toList());

		Map<Module, List<String>> mapModuleExportPackages = new LinkedHashMap<>(listOfNamedModules.size());
		List<String> listOfExportedPackages = new LinkedList<>();
		listOfNamedModules.forEach(m -> {
			mapModuleExportPackages.put(m, listExportedJavaPackages(m));
		});

		return mapModuleExportPackages;
	}

	private static Map<Module, List<String>> retrieveMapModuleNonExportedPackage() {

		List<Module> listOfNamedModules = ModuleLayer.boot().modules().stream().filter(new Predicate<Module>() {
			// remove probe module
			@Override
			public boolean test(Module m) {
				return m.equals(this.getClass().getModule()) ? false : true;
			}
		}).sorted(new Comparator<Module>() {
			@Override
			public int compare(Module m0, Module m1) {
				return m0.getName().compareTo(m1.getName());
			}
		}).collect(Collectors.toList());

		Map<Module, List<String>> mapModuleNonExportPackages = new LinkedHashMap<>(listOfNamedModules.size());
		List<String> listOfNonExportedPackages = new LinkedList<>();
		listOfNamedModules.forEach(m -> {
			mapModuleNonExportPackages.put(m, listNonExportedJavaPackages(m));
		});

		return mapModuleNonExportPackages;
	}

	private static List<String> listExportedJavaPackages(Module m) {
		return m.getPackages().stream().sorted().filter(p -> m.isExported(p)).collect(Collectors.toList());
	}

	private static List<String> listNonExportedJavaPackages(Module m) {
		return m.getPackages().stream().sorted().filter(p -> !m.isExported(p)).collect(Collectors.toList());
	}

	private static List<Package> listAllJavaPackages() {
		List<Package> listOfPackages = Arrays.asList(Package.getPackages()).stream().filter(new Predicate<Package>() {
			// remove io.klib.probe.java package
			@Override
			public boolean test(Package p) {
				return p.getName().equals(this.getClass().getPackageName()) ? false : true;
			}
		}).sorted(new Comparator<Package>() {
			@Override
			public int compare(Package p0, Package p1) {
				return p0.getName().compareTo(p1.getName());
			}
		}).collect(Collectors.toList());

		return listOfPackages;
	}

	private static String formatPackageAsBndExport(List<String> listOfPackages) {
		StringJoiner sj = new StringJoiner(";version=" + NO_VERSION + ",\\\n", "", "");
		listOfPackages.stream().sorted().forEach(p -> sj.add("  " + p));
		return sj.toString();
	}

	private static String formatPackageAsBndIgnoreList(List<String> listOfPackages) {
		StringJoiner sj = new StringJoiner("|", "", "");
		listOfPackages.stream().sorted().forEach(p -> {
			String packageAsDir = p.replaceAll("\\.", "/");
			sj.add(packageAsDir);
		});
		return "(" + sj.toString() + ")";
	}

	private static List<Path> readMyOwnJRE() throws IOException {
		List<Path> modulePaths = new LinkedList<>();
		try {
			Path p = Paths.get(URI.create(JRT_MODULES));
			modulePaths = Files.list(p).collect(Collectors.toList());
		} catch (FileSystemNotFoundException ex) {
			System.out.println("Could not read my modules (perhaps not Java >8).");
		}
		return modulePaths;
	}

	public static void readOtherJRE(Path pathToJRE) throws IOException {
		Path p = pathToJRE.resolve("lib").resolve("jrt-fs.jar");
		if (Files.exists(p)) {
			try (URLClassLoader loader = new URLClassLoader(new URL[] { p.toUri().toURL() });
					FileSystem fs = FileSystems.newFileSystem(URI.create(JRT_PROTOCOL), Collections.emptyMap(),
							loader)) {
				System.out.println("Modules of " + pathToJRE);
				Files.list(fs.getPath("/modules")).forEach(System.out::println);
				System.out.println();
			}
		}
	}

	public static Path pathTransform(final FileSystem fs, final Path path) {
		Path ret = fs.getPath(path.isAbsolute() ? fs.getSeparator() : "");
		for (final Path component : path) {
			ret = ret.resolve(component.getFileName().toString());
		}
		return ret;
	}

	public static void copyFolder(Path src, Path dest) throws IOException {
		Files.walk(src).forEach(source -> copy(source, dest.resolve(src.relativize(source))));
	}

	private static void copy(Path source, Path dest) {
		try {
			Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}
}
