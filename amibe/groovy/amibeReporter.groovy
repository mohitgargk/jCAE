/**
 * Sample class to print quality statistics about an amibe mesh.
 */
import org.jcae.mesh.amibe.ds.Mesh;
import org.jcae.mesh.amibe.ds.Triangle;
import org.jcae.mesh.xmldata.MeshReader;
import org.jcae.mesh.xmldata.MeshExporter;
import org.jcae.mesh.amibe.validation.*;
import java.lang.reflect.Constructor;
import java.io.File;
import gnu.trove.TIntHashSet;
import org.apache.commons.cli.*;

void usage(int rc, Options options)
{
	HelpFormatter formatter = new HelpFormatter();
	formatter.printHelp("groovy amibeReporter.groovy [options] xmlDir", options);
	System.exit(rc);
}

Options options = new Options();
options.addOption(
	OptionBuilder.hasArg(false)
		.withDescription("usage information")
		.withLongOpt("help")
		.create('h'));
options.addOption(
	OptionBuilder.withArgName("LIST").hasArg()
		.withDescription("comma separated list of values (default: 0.2,0.4,0.6,0.8)")
		.withLongOpt("bounds")
		.create('b'));
options.addOption(
	OptionBuilder.hasArg(false)
		.withDescription("reports statistics by face")
		.withLongOpt("detailed")
		.create('d'));
options.addOption(
	OptionBuilder.withArgName("LIST").hasArg()
		.withDescription("comma separated list of faces (default: all faces)")
		.withLongOpt("faces")
		.create('f'));
options.addOption(
	OptionBuilder.withArgName("BASE").hasArg()
		.withDescription("creates <BASE>.mesh and <BASE>.bb MEDIT files")
		.withLongOpt("output")
		.create('o'));
options.addOption(
	OptionBuilder.withArgName("CLASS").hasArg()
		.withDescription("criterion (default: MinAngleFace)")
		.withLongOpt("criterion")
		.create('c'));
options.addOption(
	OptionBuilder.withArgName("NUMBER").hasArg()
		.withDescription("scale factor (default: 1.0)")
		.withLongOpt("scale")
		.create('s'));
CommandLineParser parser = new GnuParser();
CommandLine cmd = parser.parse(options, args, true);
if (cmd.hasOption('h'))
	usage(0, options);

String [] remaining = cmd.getArgs();
if (remaining.length != 1)
	usage(1, options);

String xmlDir = remaining[0]
String xmlFile = "jcae3d";
String outBasename=cmd.getOptionValue('o');
boolean detailed = cmd.hasOption('d');

String [] sBounds=cmd.getOptionValue('b', "0.2,0.4,0.6,0.8").split(",");
Float [] bounds = new Float[sBounds.length];
for (int i = 0; i < bounds.length; i++)
	bounds[i] = Float.parseFloat(sBounds[i]);

String crit=cmd.getOptionValue('c', "MinAngleFace");
Constructor cons = Class.forName("org.jcae.mesh.amibe.validation."+crit).getConstructor();
QualityProcedure qproc = cons.newInstance();
if (qproc.getType() != QualityProcedure.FACE)
	throw new IllegalArgumentException("amibeReporter only accepts criterion on faces");

float scaleFactor=Float.parseFloat(cmd.getOptionValue('s', "1.0")).floatValue();

Mesh mesh = new Mesh(qproc.getMeshTraitsBuilder());
try
{
	MeshReader.readObject3D(mesh, xmlDir, xmlFile);
}
catch (IOException ex)
{
	println("File "+xmlDir+File.separator+"jcae3d does not exist!");
	usage();
}
// Compute mesh quality
int nrFaces = 1;
if (detailed)
{
	TIntHashSet groups = new TIntHashSet(mesh.getTriangles().size());
	for (Triangle f: mesh.getTriangles())
	{
		if (f.isWritable())
		{
			int i = f.getGroupId();
			if (i >= 0)
				groups.add(i);
		}
	}
	nrFaces = groups.size();
}
QualityFloat [] data = new QualityFloat[nrFaces];
int mean = mesh.getTriangles().size() / nrFaces;
for (int i = 0; i < data.length; i++)
{
	data[i] = new QualityFloat(mean);
	data[i].setQualityProcedure(qproc);
	data[i].setTarget(scaleFactor);
}
for (Triangle f: mesh.getTriangles())
{
	if (f.isWritable())
	{
		int i = f.getGroupId();
		if (i < 0 || !detailed)
			i = 0;
		data[i].compute(f);
	}
}
for (int i = 0; i < data.length; i++)
{
	data[i].finish();
	data[i].split(bounds);
	// Prints histogram on console
	if (detailed)
		println("Face "+(i+1));
	data[i].printLayers();
}
if (null != outBasename)
{
	// Prints triangle quality into a .bb file to be displayed by MEDIT
	if (detailed)
	{
		int [] ids = new int[1];
		for (int i = 0; i < data.length; i++)
		{
			data[i].printMeshBB(outBasename+"-"+i+".bb");
			ids[0] = i;
			new MeshExporter.MESH(new File(xmlDir), ids).write(outBasename+"-"+i+".mesh");
		}
	}
	else
	{
		data[0].printMeshBB(outBasename+".bb");
		new MeshExporter.MESH(xmlDir).write(outBasename+".mesh");
	}
}

