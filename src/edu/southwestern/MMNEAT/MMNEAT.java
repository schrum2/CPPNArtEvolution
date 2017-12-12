package edu.southwestern.MMNEAT;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import edu.southwestern.data.ResultSummaryUtilities;
import edu.southwestern.evolution.EA;
import edu.southwestern.evolution.EvolutionaryHistory;
import edu.southwestern.evolution.ScoreHistory;
import edu.southwestern.evolution.crossover.Crossover;
import edu.southwestern.evolution.genotypes.CombinedGenotype;
import edu.southwestern.evolution.genotypes.Genotype;
import edu.southwestern.evolution.genotypes.TWEANNGenotype;
import edu.southwestern.evolution.lineage.Offspring;
import edu.southwestern.evolution.metaheuristics.AntiMaxModuleUsageFitness;
import edu.southwestern.evolution.metaheuristics.FavorXModulesFitness;
import edu.southwestern.evolution.metaheuristics.LinkPenalty;
import edu.southwestern.evolution.metaheuristics.MaxModulesFitness;
import edu.southwestern.evolution.metaheuristics.Metaheuristic;
import edu.southwestern.experiment.Experiment;
import edu.southwestern.log.EvalLog;
import edu.southwestern.log.MMNEATLog;
import edu.southwestern.networks.ActivationFunctions;
import edu.southwestern.parameters.CommonConstants;
import edu.southwestern.parameters.Parameters;
import edu.southwestern.tasks.Task;
import edu.southwestern.tasks.interactive.InteractiveEvolutionTask;
import edu.southwestern.util.ClassCreation;
import edu.southwestern.util.file.FileUtilities;
import edu.southwestern.util.random.RandomGenerator;
import edu.southwestern.util.random.RandomNumbers;
import edu.southwestern.util.stats.Statistic;
import wox.serial.Easy;

/**
 * Modular Multiobjective Neuro-Evolution of Augmenting Topologies.
 * 
 * Main class that launches experiments.
 * Running "mvn -U install" will create a SNAPSHOT jar recognized by all
 * batch files.
 * 
 * @author Jacob Schrum
 */
public class MMNEAT {

	public static boolean seedExample = false;
	public static int networkInputs = 0;
	public static int networkOutputs = 0;
	public static int modesToTrack = 0;
	public static double[] lowerInputBounds;
	public static double[] upperInputBounds;
	public static int[] discreteCeilings;
	public static Experiment experiment;
	public static Task task;
	public static EA ea;
	@SuppressWarnings("rawtypes") // could hold any type, depending on command line
	public static Genotype genotype;
	@SuppressWarnings("rawtypes") // could hold any type, depending on command line
	public static ArrayList<Genotype> genotypeExamples;
	@SuppressWarnings("rawtypes") // can crossover any type, depending on command line
	public static Crossover crossoverOperator;
	@SuppressWarnings("rawtypes") // depends on genotypes
	public static ArrayList<Metaheuristic> metaheuristics;
	public static ArrayList<ArrayList<String>> fitnessFunctions;
	public static ArrayList<Statistic> aggregationOverrides;
	public static boolean blueprints = false;
	private static ArrayList<Integer> actualFitnessFunctions;
	public static TWEANNGenotype sharedMultitaskNetwork = null;
	public static TWEANNGenotype sharedPreferenceNetwork = null;
	public static EvalLog evalReport = null;
	public static RandomGenerator weightPerturber = null;
	public static MMNEATLog ghostLocationsOnPowerPillEaten = null;
	public static boolean browseLineage = false;
	
	public static MMNEAT mmneat;

	@SuppressWarnings("rawtypes")
	public static ArrayList<String> fitnessPlusMetaheuristics(int pop) {
		@SuppressWarnings("unchecked")
		ArrayList<String> result = (ArrayList<String>) fitnessFunctions.get(pop).clone();
		if(pop == 0) {
			ArrayList<String> meta = new ArrayList<String>();
			for (Metaheuristic m : metaheuristics) {
				meta.add(m.getClass().getSimpleName());
			}
			result.addAll(actualFitnessFunctions.get(pop), meta);
		}
		return result;
	}

	private static void setupSaveDirectory() {
		String saveTo = Parameters.parameters.stringParameter("saveTo");
		if (Parameters.parameters.booleanParameter("io") && !saveTo.isEmpty()) {
			String directory = FileUtilities.getSaveDirectory();
			File dir = new File(directory);
			if (!dir.exists()) {
				dir.mkdir();
			}
		}
	}

	@SuppressWarnings("rawtypes") // type of genotypes being crossed could be anything
	private static void setupCrossover() throws NoSuchMethodException {
		// Crossover operator
		if (Parameters.parameters.booleanParameter("mating")) {
			crossoverOperator = (Crossover) ClassCreation.createObject("crossover");
		}
	}

	@SuppressWarnings("rawtypes") // Metaheuristic can be applied to any type of population
	private static void setupMetaHeuristics() {
		// Metaheuristics are objectives that are not associated with the
		// domain/task
		System.out.println("Use meta-heuristics?");
		metaheuristics = new ArrayList<Metaheuristic>();
		if (Parameters.parameters.booleanParameter("antiMaxModeUsage")) {
			System.out.println("Penalize Max Mode Usage");
			metaheuristics.add(new AntiMaxModuleUsageFitness());
		}
		if (Parameters.parameters.integerParameter("numModesToPrefer") > 0) {
			int target = Parameters.parameters.integerParameter("numModesToPrefer");
			System.out.println("Prefer even usage of " + target + " modes");
			metaheuristics.add(new FavorXModulesFitness(target));
		}
		if (Parameters.parameters.booleanParameter("penalizeLinks")) {
			System.out.println("Penalize Links");
			metaheuristics.add(new LinkPenalty());
		}
		if (Parameters.parameters.booleanParameter("maximizeModes")) {
			System.out.println("Maximize Modes");
			metaheuristics.add(new MaxModulesFitness());
		}
	}

	private static void setupTWEANNGenotypeDataTracking(boolean coevolution) {
		if (genotype instanceof TWEANNGenotype || 
			genotype instanceof CombinedGenotype) { 
			if (Parameters.parameters.booleanParameter("io")
					&& Parameters.parameters.booleanParameter("logTWEANNData")) {
				System.out.println("Init TWEANN Log");
				EvolutionaryHistory.initTWEANNLog();
			}
			if (!coevolution) {
				EvolutionaryHistory.initArchetype(0);
			}

			@SuppressWarnings("rawtypes")
			long biggestInnovation = genotype instanceof CombinedGenotype ? 
					((TWEANNGenotype) ((CombinedGenotype) genotype).t1).biggestInnovation() :
					((TWEANNGenotype) genotype).biggestInnovation();
			if (biggestInnovation > EvolutionaryHistory.largestUnusedInnovationNumber) {
				EvolutionaryHistory.setInnovation(biggestInnovation + 1);
			}
		}
	}

	/**
	 * Constructor takes the command line parameters
	 * to initialize the systems parameter values.
	 * @param args directly from command line
	 */
	public MMNEAT(String[] args) {
		Parameters.initializeParameterCollections(args);
	}

	/**
	 * Constructor that takes a parameter file
	 * string to initialize the systems
	 * parameter values.
	 * @param parameterFile
	 */
	public MMNEAT(String parameterFile) {
		Parameters.initializeParameterCollections(parameterFile);
	}

	public static void registerFitnessFunction(String name) {
		registerFitnessFunction(name, 0);
	}

	/**
	 * For plotting purposes. Let simulation know that a given fitness function
	 * will be tracked.
	 * @param name Name of fitness function in plot files
	 * @param pop population index (for coevolution)
	 */
	public static void registerFitnessFunction(String name, int pop) {
		registerFitnessFunction(name, null, true, pop);
	}

	public static void registerFitnessFunction(String name, boolean affectsSelection) {
		registerFitnessFunction(name, affectsSelection, 0);
	}


	/**
	 * Like above, but indicating that the "fitness" function does not affect 
	 * selection means that it is simply an other score that is being tracked
	 * in the logs.
	 * @param name Name of score
	 * @param affectsSelection Whether or not score is actually used for selection
	 * @param pop population index (for coevolution)
	 */
	public static void registerFitnessFunction(String name, boolean affectsSelection, int pop) {
		registerFitnessFunction(name, null, affectsSelection, pop);
	}

	public static void registerFitnessFunction(String name, Statistic override, boolean affectsSelection) {
		registerFitnessFunction(name, override, affectsSelection, 0);
	}

	/**
	 * As above, but it is now possible to indicate how the score is statistically
	 * summarized when noisy evaluations occur. The default is to average scores
	 * across evaluations, but if an overriding statistic is used, then this will
	 * also be mentioned in the log.
	 * @param name Name of score column
	 * @param override Statistic applied across evaluations (null is default/average)
	 * @param affectsSelection whether it affects selection
	 * @param pop population index (for coevolution)
	 */
	public static void registerFitnessFunction(String name, Statistic override, boolean affectsSelection, int pop) {
		if(actualFitnessFunctions == null){
			actualFitnessFunctions = new ArrayList<Integer>();
		}
		while(actualFitnessFunctions.size() <= pop){
			actualFitnessFunctions.add(0);
		}
		if (affectsSelection) {
			int num = actualFitnessFunctions.get(pop) + 1;
			actualFitnessFunctions.set(pop, num);
		}
		// For coevolution.
		// Create enough objective arrays to accomadate each population
		while(fitnessFunctions.size() <= pop) {
			fitnessFunctions.add(new ArrayList<String>());
		}
		fitnessFunctions.get(pop).add(name);
		aggregationOverrides.add(override);
	}

	/**
	 * Load important classes from class parameters.
	 * Other important experiment setup also occurs.
	 * Perhaps the most important classes that always
	 * need to be loaded at the task, the experiment, 
	 * and the ea. These get stored in public static 
	 * variables of this class so they are easily accessible
	 * from all parts of the code.
	 */
	@SuppressWarnings({ "rawtypes" })
	public static void loadClasses() {
		try {
			ActivationFunctions.resetFunctionSet();
			setupSaveDirectory();

			fitnessFunctions = new ArrayList<ArrayList<String>>();
			fitnessFunctions.add(new ArrayList<String>());
			aggregationOverrides = new ArrayList<Statistic>();

			boolean loadFrom = !Parameters.parameters.stringParameter("loadFrom").equals("");
			System.out.println("Init Genotype Ids");
			EvolutionaryHistory.initGenotypeIds();
			weightPerturber = (RandomGenerator) ClassCreation.createObject("weightPerturber");

			setupCrossover();
	
			// A task is always required
			System.out.println("Set Task");
			// modesToTrack has to be set before task initialization
			int multitaskModes = CommonConstants.multitaskModules;
			if (multitaskModes > 1) {
				modesToTrack = multitaskModes;
			}
			
			task = (Task) ClassCreation.createObject("task");
			System.out.println("Load task: " + task);
			boolean multiPopulationCoevolution = false;
			// For all types of Ms Pac-Man tasks
			if (Parameters.parameters.booleanParameter("scalePillsByGen")
					&& Parameters.parameters.stringParameter("lastSavedDirectory").equals("")
					&& Parameters.parameters.integerParameter("lastSavedGeneration") == 0) {
				System.out.println("Set pre-eaten pills high, since we are scaling pills with generation");
				Parameters.parameters.setDouble("preEatenPillPercentage", 0.999);
			}
			
			// Only admissible task in CPPNArtEvolution
			if(task instanceof InteractiveEvolutionTask) {
				System.out.println("set up Interactive Evolution Task");
				InteractiveEvolutionTask temp = (InteractiveEvolutionTask) task;
				setNNInputParameters(temp.numCPPNInputs(), temp.numCPPNOutputs());
			} else if (task == null) {
				// this else statement should only happen for JUnit testing cases.
				// Some default network setup is needed.
				System.out.println("No task defined! It is assumed that this is part of a JUnit test.");
				setNNInputParameters(5, 3);
			} else {
				System.out.println("A valid task must be specified!");
				System.out.println(task);
				System.exit(1);
			}
			
			// Only loads if settings indicate that this should be used
			ScoreHistory.load();

			setupMetaHeuristics();
			// An EA is always needed. Currently only GenerationalEA classes are supported
			if (!loadFrom) {
				System.out.println("Create EA");
				ea = (EA) ClassCreation.createObject("ea");
			}
			// A Genotype to evolve with is always needed
			System.out.println("Example genotype");
			String seedGenotype = Parameters.parameters.stringParameter("seedGenotype");
			
			if (seedGenotype.isEmpty()) {
				genotype = (Genotype) ClassCreation.createObject("genotype");
			} else {
				// Copy assures a fresh genotype id
				System.out.println("Loading seed genotype: " + seedGenotype);
				genotype = ((Genotype) Easy.load(seedGenotype)).copy();
				// System.out.println(genotype);
				seedExample = true;
			}
			setupTWEANNGenotypeDataTracking(multiPopulationCoevolution);
			// An Experiment is always needed
			System.out.println("Create Experiment");
			experiment = (Experiment) ClassCreation.createObject("experiment");
			experiment.init();
			if (!loadFrom && Parameters.parameters.booleanParameter("io")) {
				if (Parameters.parameters.booleanParameter("logMutationAndLineage")) {
					EvolutionaryHistory.initLineageAndMutationLogs();
				}
			}
		} catch (Exception ex) {
			System.out.println("Exception: " + ex);
			ex.printStackTrace();
		}
	}

	/**
	 * Resets the classes used in MMNEAT and and sets them to null.
	 */
	public static void clearClasses() {
		task = null;
		metaheuristics = null;
		fitnessFunctions = null;
		aggregationOverrides = null;
		ea = null;
		genotype = null;
		experiment = null;
		EvolutionaryHistory.archetypes = null;
	}

	/**
	 * Initializes and runs the experiment given the loaded classes.
	 */
	public void run() {
		System.out.println("Run:");
		clearClasses();
		loadClasses();

		if (Parameters.parameters.booleanParameter("io")) {
			Parameters.parameters.saveParameters();
		}
		System.out.println("Run");
		experiment.run();
		System.out.println("Experiment finished");
	}

	/**
	 * Processes the task experiment for a given number of runs.
	 * @param runs
	 * @throws FileNotFoundException
	 * @throws NoSuchMethodException
	 */
	public static void process(int runs) throws FileNotFoundException, NoSuchMethodException {
		try {
			task = (Task) ClassCreation.createObject("task");
		} catch (NoSuchMethodException ex) {
			System.out.println("Failed to instantiate task " + Parameters.parameters.classParameter("task"));
			System.exit(1);
		}
		String base = Parameters.parameters.stringParameter("base");
		String saveTo = Parameters.parameters.stringParameter("saveTo");
		ResultSummaryUtilities.processExperiment(
				base + "/" + saveTo,
				Parameters.parameters.stringParameter("log"), runs, Parameters.parameters.integerParameter("maxGens"),
				"_" + "parents_log.txt",
				"_" + "parents_gen",
				base, 0);
	}

	public static void main(String[] args) throws FileNotFoundException, NoSuchMethodException {
		// Simple way of debugging using the profiler
		if (args.length == 0) {
			System.out.println("First command line parameter must be one of the following:");
			System.out.println("\tmultiple:n\twhere n is the number of experiments to run in sequence");
			System.out.println("\trunNumber:n\twhere n is the specific experiment number to assign");
			System.out.println("\tprocess:n\twhere n is the number of experiments to do data processing on");
			System.out.println("\tlineage:n\twhere n is the experiment number to do lineage browsing on");
			System.exit(0);
		}
		long start = System.currentTimeMillis();
		// Executor.main(args);
		StringTokenizer st = new StringTokenizer(args[0], ":");
		if (args[0].startsWith("multiple:")) {
			st.nextToken(); // "multiple"
			String value = st.nextToken();

			int runs = Integer.parseInt(value);
			for (int i = 0; i < runs; i++) {
				args[0] = "runNumber:" + i;
				evolutionaryRun(args);
			}
			process(runs);
		} else if (args[0].startsWith("lineage:")) {
			System.out.println("Lineage browser");
			browseLineage = true;
			st.nextToken(); // "lineage"
			String value = st.nextToken();

			int run = Integer.parseInt(value);
			args[0] = "runNumber:" + run;
			Parameters.initializeParameterCollections(args); // file should exist			
			System.out.println("Params loaded");
			String saveTo = Parameters.parameters.stringParameter("saveTo");
			String loadFrom = Parameters.parameters.stringParameter("loadFrom");
			boolean includeChildren = false;
			if (loadFrom == null || loadFrom.equals("")) {
				loadFrom = saveTo;
				includeChildren = true;
			}
			Offspring.fillInLineage(Parameters.parameters.stringParameter("base"), saveTo, run,
					Parameters.parameters.stringParameter("log"), loadFrom, includeChildren);
			Offspring.browse();
		} else if (args[0].startsWith("process:")) {
			st.nextToken(); // "process"
			String value = st.nextToken();

			int runs = Integer.parseInt(value);
			args[0] = "runNumber:0";
			Parameters.initializeParameterCollections(args); // file should exist
			loadClasses();
			process(runs);
		} else {
			evolutionaryRun(args);
		}
		System.out.println("done: " + (((System.currentTimeMillis() - start) / 1000.0) / 60.0) + " minutes");
		System.exit(0);
	}

	/**
	 * Runs a single evolution experiment with a given run number.
	 * @param args Command line parameters
	 */
	private static void evolutionaryRun(String[] args) {
		// Commandline
		mmneat = new MMNEAT(args);
		String branchRoot = Parameters.parameters.stringParameter("branchRoot");
		String lastSavedDirectory = Parameters.parameters.stringParameter("lastSavedDirectory");
		if (branchRoot != null && !branchRoot.isEmpty()
				&& (lastSavedDirectory == null || lastSavedDirectory.isEmpty())) {
			// This run is a branch off of another run.
			Parameters rootParameters = new Parameters(new String[0]);
			System.out.println("Loading root parameters from " + branchRoot);
			rootParameters.loadParameters(branchRoot);
			// Copy the needed parameters
			Parameters.parameters.setString("lastSavedDirectory", rootParameters.stringParameter("lastSavedDirectory"));
			Parameters.parameters.setString("archetype", rootParameters.stringParameter("archetype"));
			Parameters.parameters.setLong("lastInnovation", rootParameters.longParameter("lastInnovation"));
			Parameters.parameters.setLong("lastGenotypeId", rootParameters.longParameter("lastGenotypeId"));
		}
		RandomNumbers.reset();

		mmneat.run();

		closeLogs();
	}

	/**
	 * Checks for logs that aren't null, closes them and sets them to null.
	 */
	public static void closeLogs() {
		if (EvolutionaryHistory.tweannLog != null) {
			EvolutionaryHistory.tweannLog.close();
			EvolutionaryHistory.tweannLog = null;
		}
		if (EvolutionaryHistory.mutationLog != null) {
			EvolutionaryHistory.mutationLog.close();
			EvolutionaryHistory.mutationLog = null;
		}
		if (EvolutionaryHistory.lineageLog != null) {
			EvolutionaryHistory.lineageLog.close();
			EvolutionaryHistory.lineageLog = null;
		}
	}

	/**
	 * Signals that neural networks will be used, and sets them up
	 * 
	 * @param numIn
	 *            Number of task inputs to network
	 * @param numOut
	 *            Number of task outputs to network (not counting extra modes)
	 */
	public static void setNNInputParameters(int numIn, int numOut) {
		networkInputs = numIn;
		networkOutputs = numOut;
		int multitaskModes = CommonConstants.multitaskModules;
		networkOutputs *= multitaskModes;
		System.out.println("Networks will have " + networkInputs + " inputs and " + networkOutputs + " outputs.");

		lowerInputBounds = new double[networkInputs];
		upperInputBounds = new double[networkInputs];
		for (int i = 0; i < networkInputs; i++) {
			lowerInputBounds[i] = -1.0;
			upperInputBounds[i] = 1.0;
		}
	}
}
