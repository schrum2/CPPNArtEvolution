package edu.southwestern.evolution.mulambda;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import edu.southwestern.evolution.EvolutionaryHistory;
import edu.southwestern.evolution.SinglePopulationGenerationalEA;
import edu.southwestern.evolution.genotypes.Genotype;
import edu.southwestern.evolution.genotypes.TWEANNGenotype;
import edu.southwestern.log.PlotLog;
import edu.southwestern.networks.TWEANN;
import edu.southwestern.parameters.CommonConstants;
import edu.southwestern.parameters.Parameters;
import edu.southwestern.scores.Score;
import edu.southwestern.tasks.SinglePopulationTask;
import edu.southwestern.tasks.Task;
import edu.southwestern.util.PopulationUtil;

/**
 *
 * General evolutionary algorithm for one population using either a (mu +
 * lambda) or (mu,lambda) strategy.
 * 
 * @author Jacob Schrum
 * @param <T>
 *            Type of phenotype evolved
 */
public abstract class MuLambda<T> implements SinglePopulationGenerationalEA<T> {

	// Use one of these to define the mltype
	public static final int MLTYPE_PLUS = 0;
	public static final int MLTYPE_COMMA = 1;

	private final int mltype;
	public int mu;
	public int lambda;
	public SinglePopulationTask<T> task;
	public int generation;
//	protected FitnessLog<T> parentLog;
//	protected FitnessLog<T> childLog;
	protected PlotLog modeLog;
	protected boolean writeOutput;
	public boolean evaluatingParents = false;

	/**
	 * Initialize evolutionary algorithm.
	 * 
	 * @param mltype
	 *            MLTYPE_PLUS or MLTYPE_COMMA
	 * @param task
	 *            Task to evolve in
	 * @param mu
	 *            Parent population size
	 * @param lambda
	 *            Child population size
	 * @param io
	 *            Whether to write file output
	 */
	public MuLambda(int mltype, SinglePopulationTask<T> task, int mu, int lambda, boolean io) {
		this.mltype = mltype;
		this.task = task;
		this.mu = mu;
		this.lambda = lambda;
		this.generation = Parameters.parameters.integerParameter("lastSavedGeneration");
		writeOutput = Parameters.parameters.booleanParameter("io");

		// Interactive evolution logs are uninteresting
//		if (writeOutput && io) {
//			parentLog = new FitnessLog<T>("parents");
//			if (CommonConstants.logChildScores) {
//				childLog = new FitnessLog<T>("child");
//			}
//		}
	}

	/**
	 * Get task being evolved in
	 * 
	 * @return Task instance
	 */
	@Override
	public Task getTask() {
		return task;
	}

	/**
	 * Current generation
	 * 
	 * @return generation
	 */
	@Override
	public int currentGeneration() {
		return generation;
	}

	/**
	 * The initial parent population is of size mu, and is randomly generated
	 * based on an example genotype.
	 *
	 * @param example
	 *            = used to generate random genotypes
	 * @return = the initial parent population
	 */
	@Override
	public ArrayList<Genotype<T>> initialPopulation(Genotype<T> example) {
		return PopulationUtil.initialPopulation(example, mu);
	}

	/**
	 * Given parent scores/genotypes, generate children, evaluate them, and
	 * return their scores.
	 * 
	 * @param parentScores
	 *            Scores of evaluated parents (contains genotypes)
	 * @return Scores of children (contains genotypes)
	 */
	public ArrayList<Score<T>> processChildren(ArrayList<Score<T>> parentScores) {
		// Get offspring from parents
		ArrayList<Genotype<T>> children = generateChildren(lambda, parentScores);
		// Evaluate the children
		ArrayList<Score<T>> childrenScores = task.evaluateAll(children);
		return childrenScores;
	}

	/**
	 * Whether or not to perform delta coding on this generation. Depends on if
	 * setting is turned on, and how frequently it is done.
	 * 
	 * @param generation
	 *            Current generation
	 * @return Whether or not delta coding should happen
	 */
	public static boolean performDeltaCoding(int generation) {
		boolean periodicDeltaCoding = Parameters.parameters.booleanParameter("periodicDeltaCoding");
		boolean result = (periodicDeltaCoding && generation != 0
				&& generation % Parameters.parameters.integerParameter("deltaCodingFrequency") == 0);
		if (result) {
			System.out.println("Delta code children");
		}
		return result;
	}

	/**
	 * Given the scores of evaluated parents and the scores of evaluated
	 * children, generate the next parent population via selection.
	 * 
	 * @param parentScores
	 *            Parent scores (contains genotypes as well)
	 * @param childrenScores
	 *            Child scores (contains genotypes as well)
	 * @return New parent population
	 */
	public ArrayList<Genotype<T>> selectAndAdvance(ArrayList<Score<T>> parentScores, ArrayList<Score<T>> childrenScores) {
		ArrayList<Score<T>> population = prepareSourcePopulation(parentScores, childrenScores);
		ArrayList<Genotype<T>> newParents = selection(mu, population);
		EvolutionaryHistory.logMutationData("---Gen " + generation + " Over-----------------");
		EvolutionaryHistory.logLineageData("---Gen " + generation + " Over-----------------");
		generation++;
		EvolutionaryHistory.frozenPreferenceVsPolicyStatusUpdate(newParents, generation);
		CommonConstants.trialsByGenerationUpdate(generation);
		return newParents;
	}

	/**
	 * Figure out which genotypes are actually being selected from to create
	 * next generation. This method can be overridden by other EAs to do extra
	 * work to prepare the population
	 * 
	 * @param parentScores
	 *            Parent scores (contains genotypes as well)
	 * @param childrenScores
	 *            Child scores (contains genotypes as well)
	 * @return One combined list of scores of only individuals being selected
	 *         from
	 */
	public ArrayList<Score<T>> prepareSourcePopulation(ArrayList<Score<T>> parentScores, ArrayList<Score<T>> childrenScores) {
		return prepareSourcePopulation(parentScores, childrenScores, mltype);
	}

	/**
	 * Standard preparation based on Mu and Lambda
	 * 
	 * @param <T>
	 *            Type of phenotype being evolved
	 * @param parentScores
	 *            Parent scores (contains genotypes as well)
	 * @param childrenScores
	 *            Child scores (contains genotypes as well)
	 * @param mltype
	 *            MLTYPE_PLUS or MLTYPE_COMMA
	 * @return List of combined scores to perform selection on for next
	 *         generation
	 */
	public static <T> ArrayList<Score<T>> prepareSourcePopulation(ArrayList<Score<T>> parentScores, ArrayList<Score<T>> childrenScores, int mltype) {
		ArrayList<Score<T>> population = null;
		switch (mltype) {
		case MLTYPE_PLUS: // select the best from the combined parent and child population
			population = parentScores;
			population.addAll(childrenScores);
			break;
		case MLTYPE_COMMA: // select best from children alone
			population = childrenScores;
			break;
		default: // should not happen
			System.out.println("Invalid selection type for MuLambda!");
			System.exit(1);
		}
		return population;
	}

	/**
	 * Given the current parent population, return the next parent population
	 *
	 * @param parents
	 *            = current parent population
	 * @return = next parent population
	 */
	@Override
	public ArrayList<Genotype<T>> getNextGeneration(ArrayList<Genotype<T>> parents) {
		evaluatingParents = true;
		long start = System.currentTimeMillis();
		System.out.println("Eval parents: ");
		ArrayList<Score<T>> parentScores = task.evaluateAll(parents);
		long end = System.currentTimeMillis();
		System.out.println("Done parents: " + TimeUnit.MILLISECONDS.toMinutes(end - start) + " minutes");

		// Get some info about modes, if doing mode mutation
		if (TWEANN.preferenceNeuron()) {
			EvolutionaryHistory.maxModes = 0;
			EvolutionaryHistory.minModes = Integer.MAX_VALUE;

			if (parentScores.get(0).individual instanceof TWEANNGenotype) {
				for (Score<T> g : parentScores) {
					TWEANNGenotype tg = (TWEANNGenotype) g.individual;
					EvolutionaryHistory.maxModes = Math.max(tg.numModules, EvolutionaryHistory.maxModes);
					EvolutionaryHistory.minModes = Math.min(tg.numModules, EvolutionaryHistory.minModes);
				}
			}
		}

		evaluatingParents = false;
		start = System.currentTimeMillis();
		System.out.println("Eval children: ");
		ArrayList<Score<T>> childrenScores = processChildren(parentScores);
		end = System.currentTimeMillis();
		System.out.println("Done children: " + TimeUnit.MILLISECONDS.toMinutes(end - start) + " minutes");
		ArrayList<Genotype<T>> result = selectAndAdvance(parentScores, childrenScores);
		return result;
	}

	/**
	 * Because each parent is evaluated once, and each child is evaluated once
	 *
	 * @return number of evaluations per generation
	 */
	@Override
	public int evaluationsPerGeneration() {
		return mu + lambda;
	}

	/**
	 * Generate a certain number of children based on parent scores/genotypes
	 * 
	 * @param numChildren
	 *            Number of children to generate
	 * @param parentScores
	 *            List of evaluated parent scores (contains genotypes)
	 * @return numChildren of child genotypes
	 */
	public abstract ArrayList<Genotype<T>> generateChildren(int numChildren, ArrayList<Score<T>> parentScores);

	/**
	 * From the provided population of scores, filter down to a certain number
	 * of parent genotypes for the next generation.
	 * 
	 * @param numParents
	 *            Number of parents to filter down to
	 * @param scores
	 *            Scores of agents to select from (contains genotypes)
	 * @return Genotypes of filtered/selected individuals.
	 */
	public abstract ArrayList<Genotype<T>> selection(int numParents, ArrayList<Score<T>> scores);
}
