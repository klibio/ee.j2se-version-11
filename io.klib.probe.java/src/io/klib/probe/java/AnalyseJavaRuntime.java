package io.klib.probe.java;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import static org.fusesource.jansi.Ansi.*;
import static org.fusesource.jansi.Ansi.Color.*;
import static java.nio.file.StandardCopyOption.*;
import static java.nio.file.StandardOpenOption.*;

@SuppressWarnings("unused")
@Component
public class AnalyseJavaRuntime {

	private static final String ARG_JRE_PATH = "jrePath=";
	private static final String NO_VERSION = "0.0.0";
	private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");
	private static final String JRT_PROTOCOL = "jrt:/";
	private static final String JRT_MODULES = "jrt:/modules";
	private Path metaPath;
	private Path modulesPath;
	private Path packageExportPath;

	private List<String> progArgs;

	@Reference(target = "(launcher.arguments=*)")
	void args(final Object object, final Map<String, Object> map) {
		final String[] progArgsArray = (String[]) map.get("launcher.arguments");
		progArgs = progArgsArray != null ? Arrays.asList(progArgsArray) : new ArrayList<>();
	}

	@Activate
	private void activate() {
		long startTimeMillis = System.currentTimeMillis();
		String begin = String.format("launching app at %s", SIMPLE_DATE_FORMAT.format(startTimeMillis));
		System.out.println(ansi().eraseScreen().render("@|blue " + begin + "|@"));
		initDir();

		Path jrePath = getJrePath();
		extractRuntimeJar(jrePath, modulesPath);

		try {
			// @formatter:off

			// mapping of java modules to exported packages
			Map<Path, List<String>> mapModulePath2packages = getModule2PackageMap(modulesPath);
			List<String> listAllModules = mapModulePath2packages.keySet().stream().sorted()
					.map(p -> p.getFileName().toString()).collect(Collectors.toList());
			writeModule2Package(mapModulePath2packages, metaPath.resolve("1_modules2packages.txt"));

			// create file containing mapping of Java Modules to exported Packages
			List<String> listModuleExportingPackages = new LinkedList<>();

			Map<Module, List<String>> mapModuleExportedPackages = retrieveMapModuleExportedPackage();
			List<String> listAllExportedPackages = new LinkedList<>();
			mapModuleExportedPackages.keySet()
					.forEach(k -> listAllExportedPackages.addAll(mapModuleExportedPackages.get(k)));
			createFlatExportedPackageDir(listAllExportedPackages, modulesPath, packageExportPath);

			Path allExportedPackageFile = metaPath.resolve("2_allExportedPackages.txt");
			String exportAllHeader = "# all exported packages\n\n";
			String exportAllPackages = listAllExportedPackages.stream().sorted().map(p -> String.format("    %s", p))
					.collect(Collectors.joining("\n", "exported java packages:\n", "\n"));
			Files.writeString(allExportedPackageFile, exportAllHeader + exportAllPackages, StandardCharsets.UTF_8,
					CREATE, WRITE);

			String exportedModulePackageList = mapModuleExportedPackages.entrySet().stream().map(entry -> {
				String moduleName = entry.getKey().getName();
				String packageList = entry.getValue().stream().map(p -> String.format("    %s\n", p))
						.collect(Collectors.joining());
				if (packageList.length() > 0) {
					// module has package exports
					listModuleExportingPackages.add(moduleName);
					listAllExportedPackages.addAll(entry.getValue());
				}
				return String.format("# module %s\n", moduleName) + packageList;
			}).collect(Collectors.joining());
			Path moduleExportedPackageFile = metaPath.resolve("2_modules2ExportedPackages.txt");
			String exportHeader = "# Java Modules and their exported packages\n\n";
			String exportModuleHeader = listModuleExportingPackages.stream().map(m -> String.format("    %s", m))
					.collect(Collectors.joining("\n", "java modules:\n", "\n"));
			Files.writeString(moduleExportedPackageFile,
					exportHeader + exportModuleHeader + "\n" + exportedModulePackageList, StandardCharsets.UTF_8,
					CREATE, WRITE);

			List<String> listModulePrivatePackages = new LinkedList<>();
			List<String> listAllPrivatePackages = new LinkedList<>();
			Map<Module, List<String>> mapModulePrivatePackages = retrieveMapModuleNonExportedPackage();
			String privateModulePackageList = mapModulePrivatePackages.entrySet().stream().map(entry -> {
				String moduleName = entry.getKey().getName();
				String packageList = entry.getValue().stream().map(p -> String.format("    %s\n", p))
						.collect(Collectors.joining());
				if (packageList.length() > 0) {
					// module has package privates/ignores
					listModulePrivatePackages.add(moduleName);
					listAllPrivatePackages.addAll(entry.getValue());
				}
				return String.format("# module %s\n", moduleName) + packageList;
			}).collect(Collectors.joining());
			Path modulePrivatePackageFile = metaPath.resolve("3_modules2privatePackages.txt");
			String privateHeader = "# Java Modules and their private packages\n\n";
			String privateModuleHeader = listModulePrivatePackages.stream().map(m -> String.format("    %s", m))
					.collect(Collectors.joining("\n", "java modules:\n", "\n"));
			Files.writeString(modulePrivatePackageFile,
					privateHeader + privateModuleHeader + "\n" + privateModulePackageList, StandardCharsets.UTF_8,
					CREATE, WRITE);

			Path allPrivatePackageFile = metaPath.resolve("3_allPrivatePackages.txt");
			String privateAllHeader = "# all private packages\n\n";
			String privateAllPackages = listAllPrivatePackages.stream().sorted().map(p -> String.format("    %s", p))
					.collect(Collectors.joining("\n", "private java packages:\n", "\n"));
			Files.writeString(allPrivatePackageFile, privateAllHeader + privateAllPackages, StandardCharsets.UTF_8,
					CREATE, WRITE);

			// prepare bnd import file for ee.j2se bundle
			String bndSectionIncludeResource = listModuleExportingPackages.stream()
					.map(m -> String.format("    ${include_module_%s}", m))
					.collect(Collectors.joining(",\\\n", "includeresource_j2se:\\\n", "\n"));
			String bndModuleIncludeResourceList = mapModuleExportedPackages.entrySet().stream().map(e -> {
				String moduleName = e.getKey().getName();
				List<String> packageList = e.getValue();
				String moduleHeader = "";
				String packageString = "";
				if (!packageList.isEmpty()) {
					moduleHeader = String.format("include_module_%s: \\\n", moduleName);
					packageString = packageList.stream().map(p -> {
						String packageAsFile = p.replaceAll("\\.", "/");
						return String.format("    ${workspace}/io.klib.probe.java/wrk/modules/%s/%s/", moduleName,
								packageAsFile);
					}).collect(Collectors.joining(",\\\n", "", "\n\n"));
				}
				return moduleHeader + packageString;
			}).collect(Collectors.joining());

			String bndSectionModuleExport = listModuleExportingPackages.stream()
					.map(m -> String.format("    ${module_%s}", m))
					.collect(Collectors.joining(",\\\n", "j2see:\\\n", "\n\n"));
			String bndModuleExportPackageList = mapModuleExportedPackages.entrySet().stream().map(e -> {
				String moduleName = e.getKey().getName();
				List<String> packageList = e.getValue();
				String moduleHeader = "";
				String packageString = "";
				if (!packageList.isEmpty()) {
					moduleHeader = String.format("module_%s: \\\n", moduleName);
					packageString = packageList.stream().map(p -> String.format("    %s", p)).collect(Collectors
							.joining(";version=0.0.0;-noimport:=true,\\\n", "", ";version=0.0.0;-noimport:=true\n\n"));
				}
				return moduleHeader + packageString;
			}).collect(Collectors.joining());

			String bndSectionModuleIgnore = listModulePrivatePackages.stream()
					.map(m -> String.format("    ${ignore_module_%s}", m))
					.collect(Collectors.joining(",\\\n", "ignore_j2see:\\\n", "\n\n"));
			String bndModuleIgnorePackageList = mapModulePrivatePackages.entrySet().stream().map(e -> {
				String moduleName = e.getKey().getName();
				String moduleHeader = String.format("ignore_module_%s: \\\n", moduleName);
				String packageList = e.getValue().stream().map(p -> {
					String packageAsFile = p.replaceAll("\\.", "/");
					return String.format("    ${workspace}/io.klib.probe.java/wrk/modules/%s/%s/", moduleName,
							packageAsFile);
				}).collect(Collectors.joining(",\\\n", "", "\n\n"));
				return moduleHeader + packageList;
			}).collect(Collectors.joining());

			Path bndFile = metaPath.resolve("4_import_ee.j2se.txt");
			String bndHeader = "# bnd macros for JRE modules and exported packages\n\n";
			Files.writeString(bndFile,
					bndHeader + "### export_packages\n\n" + bndSectionModuleExport + "\n" + bndModuleExportPackageList
							+ "\n" + "### includeresource\n\n" + bndSectionIncludeResource + "\n"
							+ bndModuleIncludeResourceList + "\n" + "### ignore\n\n" + bndSectionModuleIgnore + "\n"
							+ bndModuleIgnorePackageList,
					StandardCharsets.UTF_8, CREATE, WRITE);
			// @formatter:on

		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.format("execution - started at %s - is finished - took %s ms",
				SIMPLE_DATE_FORMAT.format(startTimeMillis), System.currentTimeMillis() - startTimeMillis);
	}

	private void initDir() {
		Path wrkPath;
		try {
			String optTmpDir = Files.createTempDirectory("_jre_extraction_").toString();
			wrkPath = Paths.get(System.getProperty("user.dir", optTmpDir).concat("/wrk"));
			metaPath = createDir(wrkPath.resolve("meta"));
			modulesPath = createDir(wrkPath.resolve("modules"));
			packageExportPath = createDir(wrkPath.resolve("packageExports"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Path getJrePath() {
		Path jrePath = Paths.get(URI.create(JRT_MODULES));
		Optional<String> findFirst = progArgs.stream().filter(p -> p.startsWith(ARG_JRE_PATH)).findFirst();
		if (findFirst.isPresent()) {
			String jreString = findFirst.get().replaceFirst(ARG_JRE_PATH, "");
			Path jreRtLib = Paths.get(jreString).resolve("lib").resolve("jrt-fs.jar");
			if (Files.exists(jreRtLib)) {
				URLClassLoader loader;
				try {
					loader = new URLClassLoader(new URL[] { jreRtLib.toUri().toURL() });
					FileSystem fs = FileSystems.newFileSystem(URI.create(JRT_PROTOCOL), Collections.emptyMap(), loader);
					jrePath = fs.getPath("/modules");
				} catch (MalformedURLException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return jrePath;
	}

	private void writeModule2Package(Map<Path, List<String>> mapModulePath2packages, Path modulePackageFile)
			throws IOException {
		List<String> listModulePackages = new LinkedList<>();
		String modulePackageList = mapModulePath2packages.entrySet().stream().map(e -> {
			String moduleName = e.getKey().getFileName().toString();
			String packageList = e.getValue().stream().map(p -> String.format("    %s\n", p))
					.collect(Collectors.joining());
			listModulePackages.add(moduleName);
			return String.format("# module %s\n", moduleName) + packageList + "\n";
		}).collect(Collectors.joining());
		String physicalHeader = "# java modules and there extracted packages / unzipped jrt-fs.jar\n\n";
		String physicalModuleHeader = listModulePackages.stream().map(m -> String.format("    %s", m))
				.collect(Collectors.joining("\n", "java modules:\n", "\n"));
		Files.writeString(modulePackageFile, physicalHeader + physicalModuleHeader + "\n" + modulePackageList,
				StandardCharsets.UTF_8, CREATE, WRITE);
	}

	private Path createDir(Path dirPath) {
		if (!Files.exists(dirPath))
			dirPath.toFile().mkdirs();
		return dirPath;
	}

	private Map<Path, List<String>> getModule2PackageMap(Path modules) throws IOException {
		Map<Path, List<String>> mapModulePath2packages = new LinkedHashMap<>();

		List<Path> modulePathList = Files.list(modules).filter(Files::isDirectory).map(m -> m)
				.collect(Collectors.toList());
		modulePathList.forEach(m -> {
			try {
				// @formatter:off
				List<String> packageList = Files.walk(m).filter(p -> // filter only directories containing files
				p.toFile().isDirectory() && p.toFile().listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return pathname.isFile() && pathname.toString().endsWith(".class");
					}
				}).length > 0).map(d -> m.relativize(d).toString()) // package name
						.map(p -> p.replaceAll("[\\\\,/]", ".")) // . instead of path.seperator
						.filter(p -> p.length() > 0) // ? remove empty first element ?
						.collect(Collectors.toList());
				mapModulePath2packages.put(m, packageList);
				// @formatter:on
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		return mapModulePath2packages;
	}

	private static void writeFile(Path filePath, List<String> l) {

		String packageList = l.stream().map(p -> p.toString()).collect(Collectors.joining(",\n"));
		try {
			Files.writeString(filePath, packageList, StandardCharsets.UTF_8, CREATE, WRITE);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static String print2sysout(Map<Module, List<String>> mapModuleExportedPackages) {
		String modulePackageMap = "";
		modulePackageMap = mapModuleExportedPackages.entrySet().stream()
				.map(e -> String.format("# %s\n", e.getKey()).concat("    # packages\n").concat(e.getValue().stream()
						.map(p -> String.format("    %s", p.toString())).collect(Collectors.joining("\n"))))
				.collect(Collectors.joining("\n"));
		System.out.println(modulePackageMap);
		return modulePackageMap;
	}

	private void extractRuntimeJar(Path jrePath, Path extractPath) {
		// if there is already an "java.*" folder skip extraction
		File wrkDir = extractPath.toFile();
		if (!wrkDir.exists() || wrkDir.list(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.startsWith("java.");
			}
		}).length == 0) {
			try {
				Files.walk(jrePath).filter(Files::isRegularFile).forEach(f -> {
					Path classfilePath = f.subpath(1, f.getNameCount());
					Path targetModulePath = extractPath.resolve(classfilePath.toString());
					try {
						createDir(targetModulePath.getParent());
						if (!Files.exists(targetModulePath)) {
							Files.copy(classfilePath.toUri().toURL().openStream(), targetModulePath);
						} else {
							System.err.format("warning - duplicate content in JRE %s", targetModulePath);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
			} catch (FileSystemNotFoundException | IOException ex) {
				System.err.println("could not read my modules (perhaps not Java >8).");
			}
			System.out.format("successfully extracted JRE into %s\n", extractPath);
		} else {
			System.out.format("skipped extraction JRE module folder already exists inside %s\n", extractPath);
		}
	}

	private void createFlatExportedPackageDir(List<String> listAllExportedPackages, Path extractPath,
			Path flatExportPackagePath) {
		try {
			// iterate over modules
			Files.list(extractPath).forEach(m -> {
				try {
					int mCount = m.getNameCount();
					Files.walk(m).filter(Files::isDirectory).distinct().forEach(p -> {
						int pCount = p.getNameCount();
						if (pCount > mCount) {
							String packageName = p.subpath(mCount, pCount).toString().replaceAll("\\\\|/", ".");
							if (listAllExportedPackages.contains(packageName)) {
								System.out.format("copying exported package %s\n", packageName);
								try {
									Files.list(p).filter(Files::isRegularFile).forEach(f -> {
										int fCount = f.getNameCount();
										Path classfilePath = f.subpath(mCount, fCount);
										Path targetFilePath = flatExportPackagePath.resolve(classfilePath.toString());
										System.out.format("copying %s to %s\n", classfilePath, targetFilePath);
										try {
											Path packagePath = targetFilePath.getParent();
											createDir(packagePath);
											if (!Files.exists(targetFilePath)) {
												Files.copy(f.toUri().toURL().openStream(), targetFilePath);
											} else {
												System.err.format("warning - duplicate content in JRE - %s\n", f);
											}
										} catch (IOException e) {
											e.printStackTrace();
										}
									});
								} catch (IOException e1) {
									System.err.format("could not read module dir %s\n%s\n", p, e1);
								}
							} else {
								System.out.format("skip private package %s\n", packageName);
							}
						}
					});

				} catch (IOException e) {
					System.err.format("could not read module dir %s\n%s\n", m, e);
				}
			});

		} catch (FileSystemNotFoundException | IOException ex) {
			System.err.println("could not read my JRE (perhaps not Java >8).");
		}
		System.out.format("# completed creation of flat exported packages\n");
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
			Files.copy(source, dest, REPLACE_EXISTING);
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage(), e);
		}
	}

	public static void main(String[] args) throws IOException {
		new AnalyseJavaRuntime().activate();
	}

}
