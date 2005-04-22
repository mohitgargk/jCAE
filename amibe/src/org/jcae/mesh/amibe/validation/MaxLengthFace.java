/* jCAE stand for Java Computer Aided Engineering. Features are : Small CAD
   modeler, Finit element mesher, Plugin architecture.

    Copyright (C) 2005
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
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA */

package org.jcae.mesh.amibe.validation;

import org.jcae.mesh.amibe.ds.MFace3D;
import org.jcae.mesh.amibe.ds.MNode3D;
import java.util.Iterator;

public class MaxLengthFace extends QualityProcedure
{
	public MaxLengthFace()
	{
		setType(QualityProcedure.FACE);
	}
	
	public float quality(Object o)
	{
		if (!(o instanceof MFace3D))
			throw new IllegalArgumentException();
		
		double l1, l2, l3;
		MFace3D f = (MFace3D) o;
		Iterator itn = f.getNodesIterator();
		MNode3D n1 = (MNode3D) itn.next();
		MNode3D n2 = (MNode3D) itn.next();
		MNode3D n3 = (MNode3D) itn.next();
		l1 = n1.distance(n2);
		l2 = n2.distance(n3);
		if (l2 > l1)
			l1 = l2;
		l3 = n3.distance(n1);
		if (l3 > l1)
			l1 = l3;
		return (float) l1;
	}
	
}
