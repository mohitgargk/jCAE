/* jCAE stand for Java Computer Aided Engineering. Features are : Small CAD
   modeler, Finit element mesher, Plugin architecture.

    Copyright (C) 2003,2004,2005
                  Jerome Robert <jeromerobert@users.sourceforge.net>

    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.jcae.mesh.amibe.algos2d;

import org.jcae.mesh.amibe.ds.*;
import org.jcae.mesh.amibe.metrics.Metric3D;
import java.util.ArrayList;
import java.util.Iterator;
import org.apache.log4j.Logger;

/**
 * Performs an initial surface triangulation.
 * The value of discretisation is provided by the constraint hypothesis.
 */

public class EnforceAbsDeflection
{
	private static Logger logger=Logger.getLogger(EnforceAbsDeflection.class);
	private Mesh mesh = null;
	
	/**
	 * Creates a <code>EnforceAbsDeflection</code> instance.
	 *
	 * @param m  the <code>EnforceAbsDeflection</code> instance to check.
	 */
	public EnforceAbsDeflection(Mesh m)
	{
		mesh = m;
	}
	
	/**
	 * Launch method to mesh a surface.
	 */
	public void compute()
	{
		mesh.pushCompGeom(3);
		logger.debug(" Enforcing absolute deflection");

		Triangle t;
		MNode3D [] p = new MNode3D[4];
		double [] v1 = new double[3];
		double [] v2 = new double[3];
		double [] v3 = new double[3];
		boolean redo = false;
		int niter = mesh.getTriangles().size();
		double defl = Metric3D.getDeflection();
		do
		{
			redo = false;
			ArrayList badTriangles = new ArrayList();
			for (Iterator it = mesh.getTriangles().iterator(); it.hasNext(); )
			{
				t = (Triangle) it.next();
				if (t.isOuter())
					continue;
				double uv[] = t.centroid().getUV();
				double [] xyz = mesh.getGeomSurface().value(uv[0], uv[1]);
				p[3] = new MNode3D(xyz, -1);
				for (int i = 0; i < 3; i++)
				{
					uv = t.vertex[i].getUV();
					xyz = mesh.getGeomSurface().value(uv[0], uv[1]);
					p[i] = new MNode3D(xyz, -1);
				}
				double [] xyz0 = p[0].getXYZ();
				double [] xyz1 = p[1].getXYZ();
				double [] xyz2 = p[2].getXYZ();
				double [] xyz3 = p[3].getXYZ();
				for (int i = 0; i < 3; i++)
				{
					v1[i] = xyz1[i] - xyz0[i];
					v2[i] = xyz2[i] - xyz0[i];
					v3[i] = xyz3[i] - xyz0[i];
				}
				double [] vec = Metric3D.prodVect3D(v1, v2);
				double norm = Metric3D.norm(vec);
				if (norm > 0.0)
				{
					double dist = Math.abs(Metric3D.prodSca(vec, v3));
					dist /= Metric3D.norm(vec);
					if (dist > defl)
						badTriangles.add(t);
				}
			}
			for (Iterator it = badTriangles.iterator(); it.hasNext(); )
			{
				t = (Triangle) it.next();
				if (!mesh.getTriangles().contains(t))
					continue;
				double uv[] = t.centroid().getUV();
				Vertex v = new Vertex(uv[0], uv[1]);
				OTriangle vt = v.getSurroundingOTriangle();
				if (vt.split3(v, false))
				{
					mesh.getQuadTree().add(v);
					redo = true;
				}
			}
			niter--;
			logger.debug(" Found "+badTriangles.size()+" non-conforming triangles");
		} while (redo && niter > 0);
		mesh.popCompGeom(3);
	}
	
}
