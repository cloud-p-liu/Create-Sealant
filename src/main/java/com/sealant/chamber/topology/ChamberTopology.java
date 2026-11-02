package com.sealant.chamber.topology;

import net.minecraft.world.phys.AABB;
import java.util.*;

/** Plot-space topology shared by server fluid and client lighting. Owner is part of connectivity. */
public final class ChamberTopology {
    public record Node(UUID id,UUID owner,AABB box) {}
    public record Group(UUID id,UUID owner,List<Node> members,ChamberUnion union) {}
    public static List<Group> build(Collection<Node> input) {
        List<Node> nodes=new ArrayList<>(input);nodes.sort(Comparator.comparing(Node::id));
        boolean[] seen=new boolean[nodes.size()];List<Group> groups=new ArrayList<>();
        for(int start=0;start<nodes.size();start++)if(!seen[start]) {
            List<Node> members=new ArrayList<>();ArrayDeque<Integer> queue=new ArrayDeque<>();queue.add(start);seen[start]=true;
            while(!queue.isEmpty()) {
                Node node=nodes.get(queue.removeFirst());members.add(node);
                for(int i=0;i<nodes.size();i++)if(!seen[i] && node.owner.equals(nodes.get(i).owner) && connected(node.box,nodes.get(i).box)) {
                    seen[i]=true;queue.add(i);
                }
            }
            members.sort(Comparator.comparing(Node::id));
            groups.add(new Group(members.getFirst().id,members.getFirst().owner,List.copyOf(members),new ChamberUnion(members)));
        }
        return List.copyOf(groups);
    }
    /** Positive-volume overlap or a shared face with positive area; edge/corner contact has no aperture. */
    public static boolean connected(AABB a,AABB b) {
        double x=Math.min(a.maxX,b.maxX)-Math.max(a.minX,b.minX);
        double y=Math.min(a.maxY,b.maxY)-Math.max(a.minY,b.minY);
        double z=Math.min(a.maxZ,b.maxZ)-Math.max(a.minZ,b.minZ);
        return x>=0 && y>=0 && z>=0 && (x>1e-8?1:0)+(y>1e-8?1:0)+(z>1e-8?1:0)>=2;
    }
    private ChamberTopology() {}
}
