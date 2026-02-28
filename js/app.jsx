import { useState, useCallback } from 'react';
import { createRoot } from 'react-dom/client';
import {
  ReactFlow,
  Background,
  Controls,
  applyNodeChanges,
  applyEdgeChanges,
  addEdge,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

const initialNodes = [
  { id: 'node-1', position: { x: 100, y: 100 }, data: { label: 'Start' } },
  { id: 'node-2', position: { x: 100, y: 250 }, data: { label: 'Process' } },
  { id: 'node-3', position: { x: 300, y: 250 }, data: { label: 'End' } },
];

const initialEdges = [
  { id: 'edge-1-2', source: 'node-1', target: 'node-2' },
  { id: 'edge-2-3', source: 'node-2', target: 'node-3' },
];

function App() {
  const [nodes, setNodes] = useState(initialNodes);
  const [edges, setEdges] = useState(initialEdges);

  const onNodesChange = useCallback(
    (changes) => setNodes((nds) => applyNodeChanges(changes, nds)),
    [],
  );

  const onEdgesChange = useCallback(
    (changes) => setEdges((eds) => applyEdgeChanges(changes, eds)),
    [],
  );

  const onConnect = useCallback(
    (params) => setEdges((eds) => addEdge(params, eds)),
    [],
  );

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={onConnect}
      fitView
    >
      <Background />
      <Controls />
    </ReactFlow>
  );
}

const root = createRoot(document.getElementById('react-flow-root'));
root.render(<App />);
