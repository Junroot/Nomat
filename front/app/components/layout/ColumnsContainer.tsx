interface NavigationContent extends React.PropsWithChildren {}

const ColumnsContainer: React.FC<NavigationContent> = ({children}) => {
     return <div
     className="w-full flex flex-col md:flex-row md:flex-nowrap overflow-auto"
    >
        {children}
    </div>
}

export default ColumnsContainer
